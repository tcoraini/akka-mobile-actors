package se.scalablesolutions.akka.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.PreDestroy;
import javax.annotation.PostConstruct;

import se.scalablesolutions.akka.actor.*;

public class Pojo extends TypedActor implements PojoInf, ApplicationContextAware {

    private String stringFromVal;
    private String stringFromRef;

    private boolean gotApplicationContext = false;
    private boolean initInvoked = false;

    public boolean gotApplicationContext() {
      return gotApplicationContext;
    }

    public void setApplicationContext(ApplicationContext context) {
      gotApplicationContext = true;
    }

    public String getStringFromVal() {
      return stringFromVal;
    }

    public void setStringFromVal(String s) {
      stringFromVal = s;
    }

    public String getStringFromRef() {
      return stringFromRef;
    }

    public void setStringFromRef(String s) {
      stringFromRef = s;
    }

    @Override
    public void init() {
      initInvoked = true;
    }

    public boolean isInitInvoked() {
      return initInvoked;
    }
}
