package org.ods.orchestration.util

class HtmlFormatterUtil {

    static String toUl(Map<String, Object> map, String emptyDefault, String cssClass = 'inner-ul') {
        return itemsUl(map, emptyDefault, cssClass) {
            def value = it.value in List
                    ? toUl(it.value as List, emptyDefault, cssClass)
                    : it.value ?: emptyDefault

            return "<li>${it.key}: ${value}</li>"
        }
    }

    static String toUl(List<Object> list, String emptyDefault, String cssClass = 'inner-ul') {
        return itemsUl(list, emptyDefault, cssClass) { "<li>${it}</li>" }
    }

    private static String itemsUl(items, String emptyDefault, String cssClass, Closure formatItem) {
        if (!items) {
            return emptyDefault
        }

        String body = items.collect { formatItem(it) }.join()

        return "<ul class='${cssClass}'>${body}</ul>"
    }

}
