package com.m3.octoparts.cache.client

import com.m3.octoparts.cache.key._
import play.api.Logger
import shade.memcached.Codec
import skinny.util.LTSV

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.control.NonFatal

/**
 * A [[CacheAccessor]] implementation that performs the following Memcached-specific processing:
 * - converts the cache key to a String that can be used as a Memcached key
 * - skips cache PUTs with TTLs < 1 second
 * - adds error handling for any exceptions thrown synchronously by Spymemcached/Shade
 *
 * @param memcached the raw cache
 * @param keyGen the key generator
 */
class MemcachedAccessor(memcached: RawCache, keyGen: MemcachedKeyGenerator)(implicit executionContext: ExecutionContext)
    extends CacheAccessor {

  /**
   * This value is arbitrarily chosen.
   * We could also use Duration.Inf, which Shade would convert to 365 days,
   * or Duration.Zero, which Memcached would treat as infinite.
   */
  private val VERY_LONG_TTL = 30 days

  private def serializeKey(key: CacheKey) = keyGen.toMemcachedKey(key)

  def doGet[T](key: CacheKey)(implicit codec: Codec[T]): Future[Option[T]] = {
    try {
      memcached.get[T](serializeKey(key)).recoverWith {
        case NonFatal(err) => throw new CacheException(key, err)
      }
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  def doPut[T](key: CacheKey, v: T, ttl: Option[Duration])(implicit codec: Codec[T]): Future[Unit] = {
    try {
      ttl match {
        case Some(duration) if duration < 1.second =>
          /*
           * If the TTL is less than one second, we should not perform a cache insert, because:
           *  - there is no point, as the element has already expired, or will do very soon
           *  - The TTL will get rounded down to 0, which Memcached treats as meaning "infinite". This is the exact opposite to what we want.
           */
          Logger.debug(LTSV.dump("message" -> "Skipping cache PUT because ttl is less than 1 second", "key" -> key.toString, "ttl" -> duration.toString))
          Future.successful(())
        case _ =>
          memcached.set[T](serializeKey(key), v, ttl.getOrElse(VERY_LONG_TTL)).recoverWith {
            case NonFatal(err) => throw new CacheException(key, err)
          }
      }
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

}
