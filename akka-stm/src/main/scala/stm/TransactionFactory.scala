/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package se.scalablesolutions.akka.stm

import java.lang.{Boolean => JBoolean}

import se.scalablesolutions.akka.config.Config._
import se.scalablesolutions.akka.util.Duration

import org.multiverse.api.GlobalStmInstance.getGlobalStmInstance
import org.multiverse.stms.alpha.AlphaStm
import org.multiverse.templates.TransactionBoilerplate
import org.multiverse.api.{PropagationLevel => Propagation}
import org.multiverse.api.TraceLevel

/**
 * Wrapper for transaction config, factory, and boilerplate. Used by atomic.
 */
object TransactionFactory {
  def apply(config: TransactionConfig): AnyRef = new TransactionFactory(config)

  def apply(config: TransactionConfig, defaultName: String): AnyRef = new TransactionFactory(config, defaultName)

  def apply(familyName: String       = TransactionConfig.FAMILY_NAME,
            readonly: JBoolean       = TransactionConfig.READONLY,
            maxRetries: Int          = TransactionConfig.MAX_RETRIES,
            timeout: Duration        = TransactionConfig.DefaultTimeout,
            trackReads: JBoolean     = TransactionConfig.TRACK_READS,
            writeSkew: Boolean       = TransactionConfig.WRITE_SKEW,
            blockingAllowed: Boolean = TransactionConfig.BLOCKING_ALLOWED,
            interruptible: Boolean   = TransactionConfig.INTERRUPTIBLE,
            speculative: Boolean     = TransactionConfig.SPECULATIVE,
            quickRelease: Boolean    = TransactionConfig.QUICK_RELEASE,
            propagation: String      = TransactionConfig.PROPAGATION,
            traceLevel: String       = TransactionConfig.TRACE_LEVEL,
            hooks: Boolean           = TransactionConfig.HOOKS): AnyRef = {
    val config = new TransactionConfig(
      familyName, readonly, maxRetries, timeout, trackReads, writeSkew, blockingAllowed,
      interruptible, speculative, quickRelease, propagation, traceLevel, hooks)
    new TransactionFactory(config)
  }
}

/**
 * Wrapper for transaction config, factory, and boilerplate. Used by atomic.
 * Can be passed to atomic implicitly or explicitly.
 * <p/>
 * <pre>
 * implicit val txFactory = TransactionFactory(readonly = true)
 * ...
 * atomic {
 *   // do something within a readonly transaction
 * }
 * </pre>
 * <p/>
 * Can be created at different levels as needed. For example: as an implicit object
 * used throughout a package, as a static implicit val within a singleton object and
 * imported where needed, or as an implicit val within each instance of a class.
 * <p/>
 * If no explicit transaction factory is passed to atomic and there is no implicit
 * transaction factory in scope, then a default transaction factory is used.
 *
 * @see TransactionConfig for configuration options.
 */
class TransactionFactory(
  val config: TransactionConfig = DefaultTransactionConfig,
  defaultName: String = TransactionConfig.FAMILY_NAME) { self =>

  // use the config family name if it's been set, otherwise defaultName - used by actors to set class name as default
  val familyName = if (config.familyName != TransactionConfig.FAMILY_NAME) config.familyName else defaultName

  val factory = {
    var builder = (getGlobalStmInstance().asInstanceOf[AlphaStm].getTransactionFactoryBuilder()
                   .setFamilyName(familyName)
                   .setMaxRetries(config.maxRetries)
                   .setTimeoutNs(config.timeout.toNanos)
                   .setWriteSkewAllowed(config.writeSkew)
                   .setExplicitRetryAllowed(config.blockingAllowed)
                   .setInterruptible(config.interruptible)
                   .setSpeculativeConfigurationEnabled(config.speculative)
                   .setQuickReleaseEnabled(config.quickRelease)
                   .setPropagationLevel(toMultiversePropagation(config.propagation))
                   .setTraceLevel(toMultiverseTraceLevel(config.traceLevel)))

    if (config.readonly ne null) {
      builder = builder.setReadonly(config.readonly.booleanValue)
    } // otherwise default to Multiverse inference

    if (config.trackReads ne null) {
      builder = builder.setReadTrackingEnabled(config.trackReads.booleanValue)
    } // otherwise default to Multiverse inference

    builder.build()
  }

  val boilerplate = new TransactionBoilerplate(factory)

  def addHooks = if (config.hooks) Transaction.attach

  def toMultiversePropagation(level: String) = level.toLowerCase match {
    case "requiresnew" => Transaction.Propagation.RequiresNew
    case "fine"        => Transaction.Propagation.Mandatory
    case "supports"    => Transaction.Propagation.Supports
    case "never"       => Transaction.Propagation.Never
    case _             => Transaction.Propagation.Requires
  }

  def toMultiverseTraceLevel(level: String) = level.toLowerCase match {
    case "coarse" | "course" => Transaction.TraceLevel.Coarse
    case "fine"              => Transaction.TraceLevel.Fine
    case _                   => Transaction.TraceLevel.None
  }
}
