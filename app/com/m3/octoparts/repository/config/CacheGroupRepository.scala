package com.m3.octoparts.repository.config

import com.m3.octoparts.model.config._
import scalikejdbc._
import skinny.orm._
import skinny.orm.feature.{ CRUDFeatureWithId, TimestampsFeature }
import skinny.{ ParamType => SkinnyParamType }

object CacheGroupRepository extends ConfigMapper[CacheGroup] with TimestampsFeature[CacheGroup] {

  protected val permittedFields = Seq(
    "owner" -> SkinnyParamType.String,
    "name" -> SkinnyParamType.String,
    "description" -> SkinnyParamType.String
  )

  override lazy val defaultAlias = createAlias("cache_group")

  override lazy val tableName = "cache_group"

  /*
    .byDefault is activated on the other end of these hasManyThrough relationships:
    adding it here will break queries at runtime, so don't do it.

    See https://github.com/skinny-framework/skinny-framework/commit/13a87c7a3f671c3c77535426ead117cf15e431a9

    We need to use this verbose form of hasManyThrough to help SkinnyORM NOT to bump into weird table
    alias collision errors
   */
  lazy val httpPartConfigsRef = hasManyThroughWithFk[HttpPartConfig](
    through = HttpPartConfigCacheGroupRepository,
    many = HttpPartConfigRepository,
    throughFk = "cacheGroupId",
    manyFk = "httpPartConfigId",
    merge = (group, partConfigs) => group.copy(httpPartConfigs = partConfigs)
  ) // DO NOT tack on .byDefault here

  // We need to use this verbose form to help SkinnyORM NOT to bump into weird table alias collision errors
  lazy val partParamsRef = hasManyThrough[PartParamCacheGroup, PartParam](
    through = PartParamCacheGroupRepository -> PartParamCacheGroupRepository.createAlias("partParamCacheGroupGroupJoin2"),
    throughOn = (m1: Alias[CacheGroup], m2: Alias[PartParamCacheGroup]) => sqls.eq(m1.id, m2.cacheGroupId),
    many = PartParamRepository -> PartParamRepository.createAlias("partParamJoin2"),
    on = (m1: Alias[PartParamCacheGroup], m2: Alias[PartParam]) => sqls.eq(m1.partParamId, m2.id),
    merge = (cacheGroup, params) => cacheGroup.copy(partParams = params)
  )

  /**
   * Shortcut for retrieving CacheGroups along with children
   *
   * Makes our lives a little easier even though models linked by hasManyThrough don't work
   * with byDefault activated on both ends of the relationship
   */
  def withChildren: CRUDFeatureWithId[Long, CacheGroup] = joins[Long](httpPartConfigsRef, partParamsRef)

  def extract(rs: WrappedResultSet, n: ResultName[CacheGroup]) = CacheGroup(
    id = rs.get(n.id),
    name = rs.get(n.name),
    owner = rs.get(n.owner),
    description = rs.get(n.description),
    createdAt = rs.get(n.createdAt),
    updatedAt = rs.get(n.updatedAt)
  )

}
