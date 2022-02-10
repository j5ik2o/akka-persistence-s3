package com.github.j5ik2o.akka.persistence.s3.base.provider

import akka.actor.DynamicAccess
import com.github.j5ik2o.akka.persistence.s3.base.config.PluginConfig
import com.github.j5ik2o.akka.persistence.s3.base.exception.PluginException
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

import scala.collection.immutable.Seq
import scala.util.{ Failure, Success }

trait AwsCredentialsProviderProvider {

  def create(): Option[AwsCredentialsProvider]

}

object AwsCredentialsProviderProvider {

  def create(dynamicAccess: DynamicAccess, pluginConfig: PluginConfig): AwsCredentialsProviderProvider = {
    val className = pluginConfig.clientConfig.awsCredentialsProviderProviderClassName
    dynamicAccess
      .createInstanceFor[AwsCredentialsProviderProvider](
        className,
        Seq(classOf[DynamicAccess] -> dynamicAccess, classOf[PluginConfig] -> pluginConfig)
      ) match {
      case Success(value) => value
      case Failure(ex) =>
        throw new PluginException("Failed to initialize AwsCredentialsProviderProvider", Some(ex))
    }
  }

  class Default(dynamicAccess: DynamicAccess, pluginConfig: PluginConfig) extends AwsCredentialsProviderProvider {
    override def create(): Option[AwsCredentialsProvider] = {
      val classNameOpt = pluginConfig.clientConfig.awsCredentialsProviderClassName
      classNameOpt.map { className =>
        dynamicAccess
          .createInstanceFor[AwsCredentialsProvider](
            className,
            Seq(
              classOf[DynamicAccess] -> dynamicAccess,
              classOf[PluginConfig]  -> pluginConfig
            )
          ) match {
          case Success(value) => value
          case Failure(ex) =>
            throw new PluginException("Failed to initialize AwsCredentialsProvider", Some(ex))
        }
      }
    }
  }

}
