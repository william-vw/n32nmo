const fs = require("fs")
const n3 = require("./parser/n3Main_nodrop.js");

let prefixUriMap = {}
let bnode_cnt = 0

class Entry {

    constructor() {
        this.start = undefined

        this.cur_term = undefined
        this.terms = [ undefined, undefined, undefined ]
        this.formula = undefined
        this.formulas = []
        this.in_rule = false
        this.rules = []
        this.triples = []
    }
}

class Stack {

    constructor() {
        this.stack = []
    }

    cur() {
        return this.stack[this.stack.length - 1]
    }

    push() {
        let entry = new Entry()
        this.stack.push(entry)

        return entry
    }

    pop() {
        return this.stack.splice(this.stack.length - 1, 1)[0]
    }
}

let stack = new Stack()
stack.push()

function emit_triple(triple) {
    let entry = stack.cur()

    let container = entry.triples
    if (entry.formula !== undefined) {
        container = entry.formula
    }
    if (!container.includes(triple)) {
        container.push(triple)
        return true
    }

    return false
}

function bnode() {
    return `bn${bnode_cnt++}`
}

function translate_term(type, term) {
    switch (type) {
        case 'iri':
            return "\"" + term + "\""

        case 'pname':
            return "\"" + prefixUriMap[term[0]] + term[1] + "\""

        case 'bnode':
        case 'qvar':
            return "?" + term

        default:
            return term
    }
}

function translate(text) {
    n3.parse(text, {
        syntaxError: function (recognizer, offendingSymbol, line, column, msg, err) {
            console.log(
                `syntaxError: ${offendingSymbol}-${line}-${column}-${msg}-${err}`
            );
        },

        unknownPrefix: function (prefix, pName, line, start, end) {
            console.log(
                `unknownPrefix:${prefix}-${pName}-${line}-${start}-${end}`
            )
        },

        consoleError: function (type, line, start, end, msg) {
            console.log(
                `consoleError: ${type}-${line}-${start}-${end}-${msg}`
            );
        },

        onTerm: function (type, term, ctx) {
            // console.log("onTerm:" + type + ": " + JSON.stringify(term));

            stack.cur().cur_term = translate_term(type, term)
        },

        onBlankNodePropertyListStart: function(ctx) {
            let node = translate_term('bnode', bnode())

            // console.log("enter", node)

            stack.push()
            stack.cur().start = ctx.start.start
            stack.cur().terms[0] = node
        },

        onBlankNodePropertyListEnd: function(ctx) {
            let prior_entry = stack.pop()
            
            // console.log("exit", prior_entry.terms[0])

            for (let triple of prior_entry.triples) {
                emit_triple(triple)
            }

            // shouldn't happen ...
            if (prior_entry.start == stack.cur().start) {
                console.log("found duplicate blank node property list; hacking our way out of it for now")
                stack.cur().terms[0] = prior_entry.terms[0]
            }

            stack.cur().cur_term = prior_entry.terms[0]
        },

        onSubjectEnd: function(ctx) {
            let entry = stack.cur()

            entry.terms[0] = entry.cur_term
        },

        onVerbEnd: function(ctx) {
            let entry = stack.cur()

            let str = text.substring(ctx.start.start, ctx.stop.stop+1)
            if (str == "=>" || str == "<=") {
                entry.in_rule = true
            } else {
                entry.terms[1] = entry.cur_term
            }
        },

        onObjectEnd: function(ctx) {
            let entry = stack.cur()

            entry.terms[2] = entry.cur_term

            const triple = `triple(${entry.terms.join(", ")})`
            emit_triple(triple)
        },

        onFormulaStart: function(ctx) {
            stack.cur().formula = []
        },

        onFormulaEnd: function(ctx) {
            let entry = stack.cur()
            if (entry.formula) {
                entry.formulas.push(entry.formula)
                entry.formula = undefined
            }

            if (entry.in_rule && entry.formulas.length > 0) {
                entry.in_rule = false

                // console.log("rule:", entry.formulas)

                const body = entry.formulas[0].join(", ")
                const head = entry.formulas[1].join(", ")
                const rule = `${head} :- ${body}`
                entry.rules.push(rule)

                entry.formulas = []
            }
        },

        onPrefix: function (prefix, uri) {
            prefix = String(prefix);
            prefix = prefix.substring(0, prefix.length - 1); // remove ":"

            uri = String(uri);
            uri = uri.substring(1, uri.length - 1); // remove "<" and ">"

            prefixUriMap[prefix] = uri
        },

        onTriple: function (ctx) {}
    });

    let output = stack.cur().rules.join(".\n") + "."
    output += "\n" + stack.cur().triples.join(".\n") + "."

    return output
}

let args = process.argv.slice(2)
let path = args[0]

let text = fs.readFileSync(path).toString()
console.log(translate(text))