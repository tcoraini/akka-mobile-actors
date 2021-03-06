package se.scalablesolutions.akka.camel;

import se.scalablesolutions.akka.actor.TypedActor;

/**
 * @author Martin Krasser
 */
public class SampleRemoteTypedConsumerImpl extends TypedActor implements SampleRemoteTypedConsumer {

    public String foo(String s) {
        return String.format("remote typed actor: %s", s);
    }

}
