package se.kry.codetest.dto;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ServiceDTO {

  private static final String DATE_FORMAT = "yyyy/MM/dd HH:mm:ss.SSS";

  private final String url;

  private final String name;

  private final Long date;

  public ServiceDTO(String url, String name) {
    this.url = url;
    this.name = name;
    this.date = new Date().getTime();
  }

  public String getUrl() {
    return url;
  }

  public String getName() {
    return name;
  }

  public Long getDate() {
    return date;
  }

  public String getDateFormatted() {
    if (this.date != null) {
      SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
      Date date = new Date();
      date.setTime(this.date);
      return sdf.format(date);
    }
    return null;
  }
}
