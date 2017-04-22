logLevel := Level.Warn

resolvers += Resolver.url("hmrc-sbt-plugin-releases",
  url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns)


addSbtPlugin("uk.gov.hmrc" % "sbt-git-stamp" % "5.3.0")

