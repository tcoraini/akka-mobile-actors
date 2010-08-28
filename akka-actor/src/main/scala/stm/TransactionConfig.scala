/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package se.scalablesolutions.akka.stm

import java.lang.{Boolean => JBoolean}

import se.scalablesolutions.akka.config.Config._
import se.scalablesolutions.akka.util.Duration

/**
 * For configuring multiverse transactions.
 */
object TransactionConfig {
  // note: null values are so that we can default to Multiverse inference when not set
  val FAMILY_NAME      = "DefaultTransaction"
  val READONLY         = null.asInstanceOf[JBoolean]
  val MAX_RETRIES      = config.getInt("akka.stm.max-retries", 1000)
  val TIMEOUT          = config.getLong("akka.stm.timeout", 10)
  val TRACK_READS      = null.asInstanceOf[JBoolean]
  val WRITE_SKEW       = config.getBool("akka.stm.write-skew", true)
  val BLOCKING_ALLOWED = config.getBool("akka.stm.blocking-allowed", false)
  val INTERRUPTIBLE    = config.getBool("akka.stm.interruptible", false)
  val SPECULATIVE      = config.getBool("akka.stm.speculative", true)
  val QUICK_RELEASE    = config.getBool("akka.stm.quick-release", true)
  val PROPAGATION      = config.getString("akka.stm.propagation", "requires")
  val TRACE_LEVEL      = config.getString("akka.stm.trace-level", "none")
  val HOOKS            = config.getBool("akka.stm.hooks", true)

  val DefaultTimeout = Duration(TIMEOUT, TIME_UNIT)

  val DefaultGlobalTransactionConfig = TransactionConfig()

  /**
   * For configuring multiverse transactions.
   *
   * @param familyName       Family name for transactions. Useful for debugging.
   * @param readonly         Sets transaction as readonly. Readonly transactions are cheaper.
   * @param maxRetries       The maximum number of times a transaction will retry.
   * @param timeout          The maximum time a transaction will block for.
   * @param trackReads       Whether all reads should be tracked. Needed for blocking operations.
   * @param writeSkew        Whether writeskew is allowed. Disable with care.
   * @param blockingAllowed  Whether explicit retries are allowed.
   * @param interruptible    Whether a blocking transaction can be interrupted.
   * @param speculative      Whether speculative configuration should be enabled.
   * @param quickRelease     Whether locks should be released as quickly as possible (before whole commit).
   * @param propagation      For controlling how nested transactions behave.
   * @param traceLevel       Transaction trace level.
   * @param hooks            Whether hooks for persistence modules and JTA should be added to the transaction.
   */
  def apply(familyName: String       = FAMILY_NAME,
            readonly: JBoolean       = READONLY,
            maxRetries: Int          = MAX_RETRIES,
            timeout: Duration        = DefaultTimeout,
            trackReads: JBoolean     = TRACK_READS,
            writeSkew: Boolean       = WRITE_SKEW,
            blockingAllowed: Boolean = BLOCKING_ALLOWED,
            interruptible: Boolean   = INTERRUPTIBLE,
            speculative: Boolean     = SPECULATIVE,
            quickRelease: Boolean    = QUICK_RELEASE,
            propagation: String      = PROPAGATION,
            traceLevel: String       = TRACE_LEVEL,
            hooks: Boolean           = HOOKS) = {
    new TransactionConfig(familyName, readonly, maxRetries, timeout, trackReads, writeSkew, 
                          blockingAllowed, interruptible, speculative, quickRelease, 
                          propagation, traceLevel, hooks)
  }
}

/**
 * For configuring multiverse transactions.
 *
 * <p>familyName      - Family name for transactions. Useful for debugging.
 * <p>readonly        - Sets transaction as readonly. Readonly transactions are cheaper.
 * <p>maxRetries      - The maximum number of times a transaction will retry.
 * <p>timeout         - The maximum time a transaction will block for.
 * <p>trackReads      - Whether all reads should be tracked. Needed for blocking operations.
 * <p>writeSkew       - Whether writeskew is allowed. Disable with care.
 * <p>blockingAllowed - Whether explicit retries are allowed.
 * <p>interruptible   - Whether a blocking transaction can be interrupted.
 * <p>speculative     - Whether speculative configuration should be enabled.
 * <p>quickRelease    - Whether locks should be released as quickly as possible (before whole commit).
 * <p>propagation     - For controlling how nested transactions behave.
 * <p>traceLevel      - Transaction trace level.
 * <p>hooks           - Whether hooks for persistence modules and JTA should be added to the transaction.
 */
class TransactionConfig(val familyName: String       = TransactionConfig.FAMILY_NAME,
                        val readonly: JBoolean       = TransactionConfig.READONLY,
                        val maxRetries: Int          = TransactionConfig.MAX_RETRIES,
                        val timeout: Duration        = TransactionConfig.DefaultTimeout,
                        val trackReads: JBoolean     = TransactionConfig.TRACK_READS,
                        val writeSkew: Boolean       = TransactionConfig.WRITE_SKEW,
                        val blockingAllowed: Boolean = TransactionConfig.BLOCKING_ALLOWED,
                        val interruptible: Boolean   = TransactionConfig.INTERRUPTIBLE,
                        val speculative: Boolean     = TransactionConfig.SPECULATIVE,
                        val quickRelease: Boolean    = TransactionConfig.QUICK_RELEASE,
                        val propagation: String      = TransactionConfig.PROPAGATION,
                        val traceLevel: String       = TransactionConfig.TRACE_LEVEL,
                        val hooks: Boolean           = TransactionConfig.HOOKS)

object DefaultTransactionConfig extends TransactionConfig

