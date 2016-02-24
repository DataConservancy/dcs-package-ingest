/*
 * Copyright 2016 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.packaging.ingest.camel;

import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

public class Helpers {

    public static <V> Expression expression(Function<Exchange, V> exp) {
        return new ExpressionBridge<>(exp);
    }

    private static class ExpressionBridge<V>
            implements Expression {

        private final Function<Exchange, V> exp;

        public ExpressionBridge(Function<Exchange, V> exp) {
            this.exp = exp;
        }

        @Override
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            return exchange.getContext().getTypeConverter()
                    .convertTo(type, exp.apply(exchange));
        }
    }

    public static Throwable exception(Exchange e) {
        return e.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
    }

    public static String headerString(Exchange e, String name) {
        return e.getIn().getHeader(name, String.class);
    }

    public static <T> T getBodyAs(Exchange e, Class<T> type) {
        return e.getIn().getBody(type);
    }

    public static <T> T getHeaderAs(Exchange e, String header, Class<T> type) {
        return e.getIn().getHeader(header, type);
    }

    public static String formatHeaders(Exchange e) {
        StringBuilder headerString = new StringBuilder("{\n");
        e.getIn().getHeaders().entrySet().forEach(entry -> headerString
                .append(entry.getKey() + ": " + entry.getValue() + "\n"));
        return headerString.append("\n}").toString();

    }

}
