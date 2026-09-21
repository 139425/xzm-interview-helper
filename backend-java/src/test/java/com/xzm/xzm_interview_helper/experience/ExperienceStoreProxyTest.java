package com.xzm.xzm_interview_helper.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class ExperienceStoreProxyTest {

  @Test
  void databaseAccessWorksThroughSpringClassProxy() {
    var jdbc = mock(JdbcTemplate.class);
    var target = new ExperienceStore(
      jdbc,
      new ObjectMapper(),
      mock(TransactionTemplate.class),
      new ExperienceProperties()
    );
    var factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    var proxy = (ExperienceStore) factory.getProxy();
    assertSame(jdbc, proxy.database());
  }
}
