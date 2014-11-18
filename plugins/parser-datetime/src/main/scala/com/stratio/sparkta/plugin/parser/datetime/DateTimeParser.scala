/**
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stratio.sparkta.plugin.parser.datetime

import java.io.Serializable
import java.util.Date

import com.stratio.sparkta.sdk.ValidatingPropertyMap._
import com.stratio.sparkta.sdk.{Event, Parser}
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}

class DateTimeParser(properties: Map[String, Serializable]) extends Parser(properties) {

  private val formatters : Map[String,DateTimeFormatter] = (
    for {
      (field, formatString) <- properties.toSeq.map(x => (x._1, x._2.toString))
      formatMethods = classOf[ISODateTimeFormat].getMethods.toSeq.map(x => (x.getName, x)).toMap
      format = formatString match {
        case "epoch" => null
        case _ => formatMethods(formatString).invoke(null).asInstanceOf[DateTimeFormatter]
      }
    } yield (field, format)
    ).toMap

  override def parse(data: Event): Event = {
    Event(data.keyMap.map({
      case (key, value) =>
        if (formatters.hasKey(key) && !value.isInstanceOf[Date]) {
          formatters(key) match {
            case null =>
              (key, new Date(value.toString.toLong))
            case formatter =>
              (key, formatters(key).parseDateTime(value.toString).toDate)
          }
        } else {
          (key, value)
        }
    }))
  }

}

