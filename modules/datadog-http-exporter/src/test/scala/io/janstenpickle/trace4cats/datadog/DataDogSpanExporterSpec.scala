package io.janstenpickle.trace4cats.datadog

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalacheck.Shrink
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class DataDogSpanExporterSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 3, maxDiscardedFactor = 50.0)

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  behavior.of("DataDogSpanExporter")

  it should "construct ids with the DataDog ExternalTraceContext" in forAll { (batch: Batch[Chunk]) =>
    val batchSpans: List[CompletedSpan] = batch.spans.toList
    val dataDogSpans: List[DataDogSpan] = DataDogSpan.fromBatch(batch).flatten
    val expectedTraceIds = batchSpans.map(span => DataDogSpan.traceId(span.context))
    val expectedSpanIds = batchSpans.map(span => DataDogSpan.spanId(span.context))
    val expectedParentIds = batchSpans.map(span => DataDogSpan.parentId(span.context))
    assert(
      dataDogSpans.exists(span => expectedTraceIds.contains(span.trace_id)) &&
        dataDogSpans.exists(span => expectedSpanIds.contains(span.span_id)) &&
        dataDogSpans.exists(span => expectedParentIds.contains(span.parent_id))
    )
  }

  it should "send spans to datadog agent without error" in forAll { (batch: Batch[Chunk]) =>
    assertResult(())(DataDogSpanExporter.blazeClient[IO, Chunk]().use(_.exportBatch(batch)).unsafeRunSync())
  }
}
