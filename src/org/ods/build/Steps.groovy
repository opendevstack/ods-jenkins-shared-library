package org.ods.build

import groovy.json.StringEscapeUtils

class Steps {

    static def evaluateArgument(String argument, HashMap context) {
        Binding binding = new Binding(context)
        GroovyShell shell = new GroovyShell(binding)
        String expression = '"' + StringEscapeUtils.escapeJava(argument) + '" as String'
        return shell.evaluate(expression)
    }

}
