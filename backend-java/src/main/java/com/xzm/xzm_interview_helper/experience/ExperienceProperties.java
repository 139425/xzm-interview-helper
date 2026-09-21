package com.xzm.xzm_interview_helper.experience;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "experience")
public class ExperienceProperties {

  private int ownerId = 0;
  private String book = "";
  private long autumnDocId = 0;
  private String cookieFile = "";
  private String seedFile = "";
  private String aiProvider = "zhipu";
  private String aiModel = "";
}
