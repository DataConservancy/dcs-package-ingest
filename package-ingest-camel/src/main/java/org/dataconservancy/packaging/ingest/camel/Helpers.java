
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
