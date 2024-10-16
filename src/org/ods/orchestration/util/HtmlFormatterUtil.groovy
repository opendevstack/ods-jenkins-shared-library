package org.ods.orchestration.util

class HtmlFormatterUtil {

    static toUl(Map<String, Object> map, String selector = "inner-ul") {
        return objectToUl(map, selector)
    }

    static toUl(List<Object> list, String selector = "inner-ul") {
        return objectToUl(list, selector)
    }

    static toSpan(Map<String, Object> map, String selector = "inner-span", String sep = ", ") {
        return objectToSpan(map, selector, sep)
    }

    static toSpan(List<Object> list, String selector = "inner-span", String sep = ", ") {
        return objectToSpan(list, selector, sep)
    }

    private static objectToUl(Object obj, String selector) {
        if (obj == null) {
            return ""
        }

        def resolve = resolver({ HtmlFormatterUtil.toUl(it, selector) })

        def li = { it ->
            it in Map.Entry
                ? "<li>${it.key}: ${resolve(it.value)}</li>"
                : "<li>${resolve(it)}</li>"
        }

        def lis = obj.collect { li(it) }.join()

        return "<ul class='${selector}'>${lis}</ul>"
    }

    private static objectToSpan(Object obj, String selector, String sep) {
        if (obj == null) {
            return ""
        }

        def resolve = resolver({ HtmlFormatterUtil.toSpan(it, selector, sep) })

        def spanContent = { it ->
            it in Map.Entry
                ? "${it.key}: ${resolve(it.value)}"
                : "${resolve(it)}"
        }

        def spanBody = obj.collect { spanContent(it) }.join(sep)

        return "<span class='${selector}'>${spanBody}</span>"
    }

    private static Closure<String> resolver(Closure<String> collectionValue) {
        { value ->
            value in Collection
                ? collectionValue(value)
                : value
        }
    }
}
