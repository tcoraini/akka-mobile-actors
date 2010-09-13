package se.scalablesolutions.akka.api;

import se.scalablesolutions.akka.actor.annotation.transactionrequired;
import se.scalablesolutions.akka.actor.annotation.inittransactionalstate;
import se.scalablesolutions.akka.stm.*;

@transactionrequired
public class InMemStatefulNested {
  private TransactionalMap<String, String> mapState;
  private TransactionalVector<String> vectorState;
  private Ref<String> refState;
  private boolean isInitialized = false;

  @inittransactionalstate
  public void init() {
    if (!isInitialized) {
      mapState = new TransactionalMap();
      vectorState = new TransactionalVector();
      refState = new Ref();
      isInitialized = true;
    }
  }

  public String getMapState(String key) {
    return (String) mapState.get(key).get();
  }


  public String getVectorState() {
    return (String) vectorState.last();
  }


  public String getRefState() {
    return (String) refState.get().get();
  }


  public void setMapState(String key, String msg) {
    mapState.put(key, msg);
  }


  public void setVectorState(String msg) {
    vectorState.add(msg);
  }


  public void setRefState(String msg) {
    refState.swap(msg);
  }


  public void success(String key, String msg) {
    mapState.put(key, msg);
    vectorState.add(msg);
    refState.swap(msg);
  }


  public String failure(String key, String msg, InMemFailer failer) {
    mapState.put(key, msg);
    vectorState.add(msg);
    refState.swap(msg);
    failer.fail();
    return msg;
  }


  public void thisMethodHangs(String key, String msg, InMemFailer failer) {
    setMapState(key, msg);
  }

  /*
  public void clashOk(String key, String msg, InMemClasher clasher) {
    mapState.put(key, msg);
    clasher.clash();
  }

  public void clashNotOk(String key, String msg, InMemClasher clasher) {
    mapState.put(key, msg);
    clasher.clash();
    this.success("clash", "clash");
  }
  */
}
