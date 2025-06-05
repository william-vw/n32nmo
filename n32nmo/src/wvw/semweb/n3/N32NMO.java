package wvw.semweb.n3;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jen3.datatypes.xsd.XSDDatatype;
import org.apache.jen3.n3.N3ModelSpec;
import org.apache.jen3.n3.N3ModelSpec.Types;
import org.apache.jen3.n3.ParserN3;
import org.apache.jen3.n3.impl.N3ModelImpl;
import org.apache.jen3.rdf.model.CitedFormula;
import org.apache.jen3.rdf.model.Literal;
import org.apache.jen3.rdf.model.Model;
import org.apache.jen3.rdf.model.QuickVariable;
import org.apache.jen3.rdf.model.Resource;
import org.apache.jen3.rdf.model.Statement;
import org.apache.jen3.rdf.model.StmtIterator;
import org.apache.jen3.shared.impl.JenaParameters;
import org.apache.jen3.vocabulary.N3Log;

public class N32NMO extends N3ModelImpl {

	public static void main(String[] args) throws Exception {
		JenaParameters.disableBNodeUIDGeneration = true;

		List<String> inPaths = Stream.of(args).limit(args.length-1).collect(Collectors.toList());
		String nmoPath = args[args.length - 1];
		
		translate(inPaths, nmoPath);
	}

	public static void translate(List<String> inPaths, String nmoPath) throws Exception {
		File nmoFile = new File(nmoPath);
		if (nmoFile.exists()) {
			nmoFile.delete();
		}

		FileWriter w = new FileWriter(nmoFile);
		for (String inPath : inPaths) {
			FileReader reader = new FileReader(new File(inPath));

			N32NMO translator = new N32NMO(N3ModelSpec.get(Types.N3_MEM));

			ParserN3 parser = new ParserN3();
			parser.parse(translator, null, reader);

			String out = translator.getNmoStatements() + "\n";
			w.write(out);
		}

		w.write("\n@export result :- csv{}.");

		w.flush();
		w.close();
	}

	private List<String> bodyVars = new ArrayList<>();
	private List<String> nmoStmts = new ArrayList<>();

	public N32NMO(N3ModelSpec spec) {
		super(spec);
	}

	public String getNmoStatements() {
		return nmoStmts.stream().collect(Collectors.joining("\n")) + "\n";
	}

	public Model add(Resource s, Resource p, Resource o) {
//		System.out.println(s + ", " + p + ", " + o);

		String nmoStmt = null;
		if (p.getURI().equals(N3Log.implies.getURI()) && s.isCitedFormula() && o.isCitedFormula()) {
			CitedFormula sf = s.asCitedFormula();
			String body = toNmo(sf.getImmutableContents(), false);

			CitedFormula of = o.asCitedFormula();
			String head = toNmo(of.getImmutableContents(), true);

			nmoStmt = head + " :- " + body;

		} else {
			nmoStmt = toNmo(s, p, o, false);
		}

		nmoStmt += " .";

		nmoStmts.add(nmoStmt);

		return this;
	}

	public String toNmo(Resource s, Resource p, Resource o, boolean head) {
		StringBuffer str = new StringBuffer();

		if (p.getURI().equals("http://example.org/spin#result")) {
			List<Resource> labelsValues = new ArrayList<>();

			Iterator<Resource> resultIt = o.asCollection().getElements();
			while (resultIt.hasNext()) {
				Iterator<Resource> labelValIt = resultIt.next().asCollection().getElements();

				labelsValues.add(labelValIt.next());
				labelsValues.add(labelValIt.next());
			}

			str.append("result(").append(toNmo(s, head)).append(", ");
			str.append(labelsValues.stream().map(e -> toNmo(e, head)).collect(Collectors.joining(", ")));
			str.append(")");

		} else {
			str.append("triple(").append(toNmo(s, head)).append(", ").append(toNmo(p, head)).append(", ")
					.append(toNmo(o, head)).append(")");
		}

		return str.toString();
	}

	public String toNmo(Resource r, boolean head) {
		switch (r.getNodeType()) {
		case URI:
			return "\"" + r.getURI() + "\"";

		case BLANK:
		case QUICK_VAR:
			String symbol = "?";
			String label = (r.isQuickVariable() ? ((QuickVariable) r).getName() : r.getId().getLabelString());

			if (head) {
				if (!bodyVars.contains(label)) {
					symbol = "!";
				}

			} else {
				bodyVars.add(label);
			}

			return symbol + label;

		case LITERAL:
			Literal l = ((Literal) r);
//			System.out.println(l.getValue() + " - " + l.getDatatypeURI());
			if (l.getDatatype().getURI().equals(XSDDatatype.XSDfloat.getURI())
					|| l.getDatatype().getURI().equals(XSDDatatype.XSDdouble.getURI())
					|| l.getDatatype().getURI().equals(XSDDatatype.XSDdecimal.getURI())
					|| l.getDatatype().getURI().equals(XSDDatatype.XSDint.getURI())
					|| l.getDatatype().getURI().equals(XSDDatatype.XSDlong.getURI())
					|| l.getDatatype().getURI().equals(XSDDatatype.XSDshort.getURI())) {

				return l.getValue().toString();

			} else {
				return "\"" + l.getValue() + "\"";
			}

		default:
			System.err.println("unsupported resource: " + r);
			return null;
		}
	}

	public String toNmo(Model m, boolean head) {
		List<String> strs = new ArrayList<>();
		m.listStatements().forEachRemaining(
				stmt -> strs.add(toNmo(stmt.getSubject(), stmt.getPredicate(), stmt.getObject(), head)));

		return strs.stream().collect(Collectors.joining(", "));
	}

	@Override
	public Model add(Statement[] statements) {
		for (Statement stmt : statements)
			add(stmt.getSubject(), stmt.getPredicate(), stmt.getObject());

		return this;
	}

	@Override
	public Model add(List<Statement> statements) {
		for (Statement stmt : statements)
			add(stmt.getSubject(), stmt.getPredicate(), stmt.getObject());

		return this;
	}

	@Override
	public Model add(StmtIterator iter) {
		return add(iter.toList());
	}
}
