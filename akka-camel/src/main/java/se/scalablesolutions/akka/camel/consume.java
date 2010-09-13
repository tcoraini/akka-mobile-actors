/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package se.scalablesolutions.akka.camel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used by implementations of {@link se.scalablesolutions.akka.actor.TypedActor}
 * (on method-level) to define consumer endpoints.
 *
 * @author Martin Krasser
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface consume {

    /**
     * Consumer endpoint URI
     */
    public abstract String value();

}
