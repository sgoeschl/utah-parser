package com.sonalake.utah;

import com.sonalake.utah.config.Config;
import com.sonalake.utah.config.Delimiter;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import static java.util.Objects.requireNonNull;

/**
 * Parse a semi-structured text file, that can defined by the config.
 */
public class Parser implements Iterable<Map<String, String>> {

  /**
   * The config.
   */
  private final Config config;

  /**
   * The source of the data
   */
  private final BufferedReader reader;
  private final TreeMap<String, String> commonRecord;
  private String previousDelim;

  private int recordNumber;

  /** Hand out only one iterator dince we can consume the <code>reader</code> only once */
  private boolean isIteratorAlreadyInstantiated = false;

  /**
   * Build the parser.
   *
   * @param config The delimiter is used to determine the end of a record.
   * @param in     the input stream, the client is responsible for closing this
   * @return a parser
   */
  public static Parser parse(Config config, Reader in) {
    return new Parser(config, in);
  }

  /**
   * Build the parser
   *
   * @param config the config
   * @param in     the input stream, the client is responsible for closing this
   */
  private Parser(Config config, Reader in) {
    this.config = config;
    this.reader = new BufferedReader(in);
    this.previousDelim = "";
    recordNumber = 0;

    commonRecord = new TreeMap<String, String>();
    if (config.hasHeaderDelim()) {
      String header = getNextRecord(true);
      commonRecord.putAll(config.buildHeader(header));
    }
  }

  /**
   * Get the next record from the file.
   *
   * @return The next record, or null if there are none
   * @deprecated Since 1.1.1, please use iterator instead
   */
  @Deprecated
  public Map<String, String> next() {
    String rawRecord = getNextRecord(false);
    if (null == rawRecord) {
      return null;
    } else {
      Map<String, String> record = config.buildRecord(rawRecord);
      record.putAll(commonRecord);
      return record;
    }
  }

  @Override
  public Iterator<Map<String, String>> iterator() {
    if(isIteratorAlreadyInstantiated) {
      throw new IllegalStateException("Only one interator per parser is supported");
    }

    isIteratorAlreadyInstantiated = true;
    return new RecordIterator(this);
  }

  /**
   * Get the next raw record
   *
   * @param isSelectingHeader True, if we're parsing the header, or false if we're parsing records
   * @return The next record, or null if there are none.
   */
  private String getNextRecord(boolean isSelectingHeader) {
    //  loop through the file until we get to the record break
    try {
      boolean isReaderFinished = false;
      boolean isRecordLoaded = false;
      StringBuilder buffer = new StringBuilder();


      // we may need to skip the first delim in some cases
      boolean wasDelimMatched = false;
      while (!isRecordLoaded) {
        String currentLine = reader.readLine();
        if (null == currentLine) {
          isReaderFinished = true;
          isRecordLoaded = true;
        } else {
          if (StringUtils.isNotBlank(previousDelim)) {
            buffer.append(previousDelim + "\n");
            previousDelim = "";
          }
          if (isSelectingHeader && config.matchesHeaderDelim(currentLine)) {
            isRecordLoaded = true;
          } else if (!isSelectingHeader && config.matchesRecordDelim(currentLine)) {
            Delimiter applicableDelim = config.getApplicableDelim(currentLine);
            // if the delimiter says we're at the start of the record,
            // and this is the first record, we need to treat it differently
            boolean isFirstDelimOfInterest = 0 == recordNumber && !wasDelimMatched;
            if (applicableDelim.isDelimAtStartOfRecord() && isFirstDelimOfInterest) {
              // this is the first record, so we don't stop here
              wasDelimMatched = true;
            } else {
              if (applicableDelim.isRetainDelim()) {
                previousDelim = currentLine;
              }
              isRecordLoaded = true;
            }
          }
        }
        if (StringUtils.isNotBlank(currentLine)) {
          buffer.append(currentLine + "\n");
        }
      }
      if (isReaderFinished && buffer.length() == 0) {
        return null;
      } else {
        recordNumber++;
        return buffer.toString();
      }
    } catch (IOException e) {
      throw new RuntimeException("Problem reading source", e);
    }
  }

  private static final class RecordIterator implements Iterator<Map<String, String>> {

    private final Parser parser;
    private Map<String, String> nextRecord;

    RecordIterator(Parser parser) {
      this.parser = requireNonNull(parser);
      this.nextRecord = parser.next();
    }

    @Override
    public boolean hasNext() {
      return nextRecord != null;
    }

    @Override
    public Map<String, String> next() {
      if (nextRecord == null) {
        throw new NoSuchElementException();
      }

      final Map<String, String> currentRecord = nextRecord;
      nextRecord = parser.next();
      return currentRecord;
    }
  }
}
