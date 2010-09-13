package se.scalablesolutions.akka.api;

import com.google.inject.Inject;

public class Foo extends se.scalablesolutions.akka.serialization.Serializable.JavaJSON {
  @Inject
  private Bar bar;
  public Foo body() { return this; }
  public Bar getBar() {
    return bar;
  }
  public String foo(String msg) {
    return msg + "return_foo ";
  }
  public void bar(String msg) {
    bar.bar(msg);
  }
  public String longRunning() {
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
    }
    return "test";
  }
  public String throwsException() {
    if (true) throw new RuntimeException("Expected exception; to test fault-tolerance");
    return "test";
  }

  public int $tag() throws java.rmi.RemoteException
  {
    return 0;
  }
}
