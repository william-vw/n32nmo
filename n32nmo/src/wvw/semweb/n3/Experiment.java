package wvw.semweb.n3;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.jen3.shared.impl.JenaParameters;

public class Experiment {

	public static void main(String[] args) throws Exception {
		JenaParameters.disableBNodeUIDGeneration = true;

		experiment();
	}

	public static void experiment() throws Exception {
		// - START parameters

		// - setup for sin3 tests

		String root = "/Users/wvw/git/n3/sparql2n3/SPARQL-to-N3/";

		// -- gmark

		String dataset = "500";
		// (these don't finish after 5 minutes or so)
		List<String> skipQueries = Arrays.asList();
		String skipUntil = null;

//		String queryName = "query-11.sparql"; // (if you want to test 1 query)

		String dataName = "gmark_" + dataset;
		String queryFolder = root + "other_systems/gmark-dominik/" + dataset + "/";
		String dataPath = root + "other_systems/gmark-dominik/" + dataset + ".nt";

		// -- general

		String tmpFolder = root + "SPIN-to-N3/test/run/tmp/";
		String outFolder = root + "SPIN-to-N3/test/run/results/";
		String resultTmpl = "%s-%s.nmo";
		String timesFile = root + "SPIN-to-N3/test/run/times/" + dataName + "-nmo.csv";
//		String cmd = "/Users/wvw/git/n3/sparql2n3/SPARQL-to-N3/SPIN-to-N3/test/run/compile_n3_forward.sh";
		String cmd = "/Users/wvw/git/n3/sparql2n3/SPARQL-to-N3/SPIN-to-N3/test/run/compile_n3_forward_noDupl.sh";
		String cmdFolder = root + "SPIN-to-N3/test/run/";

		String nmoFolder = root + "other_systems/gmark-dominik/" + dataset + "/nmo/"; // tmpFolder;

		boolean translateOnly = true;

		// - END parameters

		FileWriter fw = new FileWriter(new File(timesFile));
		fw.write("query,result_file,convert_time,exec_time\n");

//		// - run 1 query
//
//		String queryPath = queryFolder + queryName;
//		experiment(queryPath, dataName, dataPath, cmd, cmdFolder, tmpFolder, outFolder, nmoFolder, resultTmpl, fw, translateOnly);

		// - run all queries in folder

		boolean skip = (skipUntil != null);

		List<File> files = Arrays.asList(new File(queryFolder).listFiles());
		files.sort((f1, f2) -> f1.getName().compareTo(f2.getName()));
		for (File f : files) {
			if (!f.getName().endsWith(".sparql") || skipQueries.contains(f.getName())) {
				continue;
			}
			if (skip) {
				if (skipUntil.equals(f.getName())) {
					skip = false;
				} else {
					continue;
				}
			}

			String queryPath = f.getAbsolutePath();
			experiment(queryPath, dataName, dataPath, cmd, cmdFolder, tmpFolder, outFolder, nmoFolder, resultTmpl, fw,
					translateOnly);
		}

		fw.close();
	}

	public static void experiment(String queryPath, String dataName, String dataPath, String cmd, String cmdFolder,
			String tmpFolder, String outFolder, String nmoFolder, String resultTmpl, FileWriter fw,
			boolean translateOnly) throws Exception {

		String queryName = new File(queryPath).getName();
		queryName = queryName.substring(0, queryName.lastIndexOf("."));

		System.out.println("> " + queryName + " (" + LocalTime.now() + ")");

		String resultName = String.format(resultTmpl, dataName, queryName);

		long start = System.nanoTime();

		// compile n3query & runtime
		ProcessBuilder builder = new ProcessBuilder(cmd, queryPath, "");
		builder.directory(new File(cmdFolder).getAbsoluteFile());
		builder.redirectErrorStream(true);
		builder.environment().put("PATH",
				"/opt/eye/bin:/opt/homebrew/bin:/bin:/Users/wvw/.p2/pool/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.macosx.aarch64_17.0.4.v20220903-1038/jre/bin");
		Process p = builder.start();
//		watch(p);
		int ret = p.waitFor();
		if (ret > 0) {
			System.err.println("error executing compile cmd (code " + ret + ")");
		}

		// convert input data, n3query, runtime
//		String[] inPaths = { dataPath, tmpFolder + "n3query.n3", tmpFolder + "runtime.n3" };
		String[] inPaths = { dataPath, tmpFolder + "n3OptimisedQuery.n3", tmpFolder + "runtimeNoDuplicates.n3" };
		String nmoPath = nmoFolder + resultName;

		N32NMO.translate(Arrays.asList(inPaths), nmoPath);

		double conv_time = ((double) (System.nanoTime() - start)) / 1000000000; // seconds

		if (!translateOnly) {
			start = System.nanoTime();

			ProcessBuilder builder2 = new ProcessBuilder("/opt/nemo-0.6.0/nmo", nmoPath, "--export-dir", "results",
					"--overwrite-results");
			builder2.directory(new File(cmdFolder).getAbsoluteFile());
			builder2.redirectErrorStream(true);

			Process p2 = builder2.start();
//		Process p2 = Runtime.getRuntime()
//				.exec(new String[] { "/opt/nemo-0.6.0/nmo", nmoPath, "--export-dir", "results", "--overwrite-results" });
//		watch(p2);
			int ret2 = p2.waitFor();
			if (ret2 > 0) {
				System.err.println("error executing nmo cmd (code " + ret2 + ")");
			}
			double exec_time = ((double) (System.nanoTime() - start)) / 1000000000; // seconds

			fw.write(queryName + "," + resultName + "," + conv_time + "," + exec_time + "\n");
			fw.flush();

			new File(outFolder + "/result.csv").renameTo(new File(outFolder + "/" + resultName));
		}
	}

//	private static void watch(Process p) {
//		new Thread(new Runnable() {
//			public void run() {
//				BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
//				String line = null;
//
//				try {
//					while ((line = input.readLine()) != null)
//						System.out.println(line);
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
//		}).start();
//	}
}
