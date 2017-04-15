package ru.spbu.math.plok.solvers.histogramsolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.spbu.math.plok.model.client.Query;
import ru.spbu.math.plok.solvers.Solver;
import ru.spbu.math.plok.solvers.histogramsolver.UserChoice.Policy;
import ru.spbu.math.plok.utils.Triplet;


public class HistogramSolver extends Solver{

	private static final String POLICIES_PARAMS = null;
	private static final int FLAT_THRESHOLD = 15;
	private static final int J_THRESHOLD = 15;
	private final static Logger log = LoggerFactory.getLogger(HistogramSolver.class);
	private HParser parser;
	private int    cacheUnitSize;
	private int    iMax  = Integer.MIN_VALUE;
	private int    iMin  = Integer.MAX_VALUE;
	private long   jMin  = Long.MAX_VALUE;;
	private long   jMax  = Long.MIN_VALUE;
	private long   tBeg  = Long.MIN_VALUE;
	private long   tEnd  = Long.MAX_VALUE;
	private Policy iPolicy;
	private Policy jPolicy;
	private Map<String, Object> policiesParams;
	private int P;
	private int L;

	private Histogram<Integer> i1Hist;
	private Histogram<Integer> i2Hist;
	private Histogram<Integer> iLHist;
	private Histogram<Long>    j1Hist;
	private Histogram<Long>    j2Hist;
	private Histogram<Long>    jLHist;
	private Histogram<Double>  jRHist;
	private ArrayList<Query> queries;


	public HistogramSolver(String historyFile, int cacheUnitSize){
		super(historyFile);
		this.cacheUnitSize = cacheUnitSize;
		this.policiesParams = new HashMap<>();
	}

	public HashMap<String, Object> solvePLTask() throws IOException {
		this.parser = new HParser();
		File historyFile = new File(Paths.get(H).toAbsolutePath().toString());
		HashMap<String, Object> report = new HashMap<>();
		analyzeFileData(historyFile);
		calculatePL();
		log.debug("Calculated P={}, L={}", P , L);
		report.put(P_KEY,       P);
		report.put(L_KEY,       L);
		report.put(I_MIN_KEY,   iMin);
		report.put(J_MIN_KEY,   jMin);
		report.put(I_MAX_KEY,   iMax);
		report.put(J_MAX_KEY,   jMax);
		report.put(QUERIES_KEY, queries);
		report.put(POLICIES_PARAMS, policiesParams);
		return report;
	}


	private void analyzeFileData(File file) throws IOException {
		String line;
		int  lineNumber = 0;
		boolean hintsProvided = false;
		queries = new ArrayList<>();
		try(BufferedReader reader = new BufferedReader(new FileReader(file))){
			while ((line = reader.readLine()) != null){
				lineNumber++;
				if (parser.isValidHistoryLine(line)){
					Query query = parser.getNextUserQuery(line);
					queries.add(query);
					relaxExtremes(query);
				}else if (parser.isHint(line)){
					log.debug("Hints detected!");
					String[] hints = parser.checkAndParseHints(line);
					if (hints != null){
						hintsProvided = true;
						setPolicies(hints[0], hints[1]);
					}
				}else{
					log.info("Line {} ignored: {}", lineNumber, line);
				}
			}
			buildHistograms();
			if (!hintsProvided){
				guessPolicies();
			}
			log.debug(i1Hist.toString());
			log.debug(i2Hist.toString());
			log.debug(j1Hist.toString());
			log.debug(j2Hist.toString());
			log.debug(iLHist.toString());
			log.debug(jLHist.toString());
			log.debug(jRHist.toString());
			log.debug("Estimated iPolicy: {}", iPolicy);
			log.debug("Estimated jPolicy: {}", jPolicy);

		}catch(Exception er){
			log.error("Error at line {}: {}", lineNumber, er);
			throw er;
		}
	}

	private void relaxExtremes(Query query) {
		if (query.getTime() <= tBeg)
			tBeg = query.getTime();
		if (query.getTime() >= tEnd)
			tBeg = query.getTime();
		if (query.getI1() <= iMin)
			iMin = query.getI1();
		if (query.getJ1() <= jMin)
			jMin = query.getJ1();
		if (query.getI2() >= iMax)
			iMax = query.getI2();
		if (query.getJ2() >= jMax)
			jMax = query.getJ2();
	}

	private void buildHistograms() {
		ArrayList<Integer> i1Data = new ArrayList<Integer>(queries.size());
		ArrayList<Integer> i2Data = new ArrayList<Integer>(queries.size());
		ArrayList<Long>    j1Data = new ArrayList<Long>(queries.size());
		ArrayList<Long>    j2Data = new ArrayList<Long>(queries.size());
		ArrayList<Integer> iLData = new ArrayList<Integer>(queries.size());
		ArrayList<Long>    jLData = new ArrayList<Long>(queries.size());
		ArrayList<Double>  jRData = new ArrayList<Double>(queries.size());
		for (Query query : queries){
			i1Data.add(query.getI1());
			i2Data.add(query.getI2());
			j1Data.add(query.getJ1());
			j2Data.add(query.getJ2());
			iLData.add(query.getILength());
			jLData.add(query.getJLength());
			jRData.add((double)query.getJ2() / query.getTime());	
		}
		i1Hist = new Histogram<Integer>("I1 HISTOGRAM", i1Data, iMin, iMax);
		j1Hist = new Histogram<Long>   ("J1 HISTOGRAM", j1Data, jMin, jMax);
		i2Hist = new Histogram<Integer>("I2 HISTOGRAM", i2Data, iMin, iMax);
		j2Hist = new Histogram<Long>   ("J2 HISTOGRAM", j2Data, jMin, jMax);
		iLHist = new Histogram<Integer>("INDEX RANGE LENGTH HISTOGRAM",         iLData, 0,   iMax - iMin + 1);
		jLHist = new Histogram<Long>   ("TIME RANGE LENGTH HISTOGRAM",          jLData, 0L,  jMax - jMin + 1);
		jRHist = new Histogram<Double> ("RELATIVE TIME RANGE LENGTH HISTOGRAM", jRData, 0.0, 1.0);
		
		i1Hist.normalizeToPercents();
		j1Hist.normalizeToPercents();
		i2Hist.normalizeToPercents();
		j2Hist.normalizeToPercents();
		iLHist.normalizeToPercents();
		jLHist.normalizeToPercents();
		jRHist.normalizeToPercents();
	}


	private void setPolicies(String iHint, String jHint) {
		for (Policy policy : Policy.values()){
			if (iHint.equalsIgnoreCase(policy.toString())){
				iPolicy = policy;
			}
			if (jHint.equalsIgnoreCase(policy.toString())){
				jPolicy = policy;
			}
		}
	}

	private void guessPolicies() {
		log.debug("Guessing policies...");
		guessIPolicy();
		guessJPolicy();
	}

	private void guessJPolicy() {
		Bin maxBin = jRHist.getMaxBin();
		int lastNzId = jRHist.getLastNonZeroBin();
		if (maxBin.getId() == lastNzId &&
		    maxBin.getValue() - jRHist.getBin(lastNzId - 1).getValue() > J_THRESHOLD){
			this.jPolicy = Policy.RECENT_TRACKING;
			this.policiesParams.put(J_POLICY_RT_WINDOW_KEY, jRHist.getMaxRawForBin(maxBin.getId()));
		}else{
			this.jPolicy = Policy.FULL_TRACKING;
		}
		
	}

	private void guessIPolicy() {
		if (isFlatEnough(i1Hist) && isFlatEnough(i2Hist)){
			this.iPolicy = Policy.FULL_TRACKING;
		}else{
			List<Triplet<Integer>> islands = this.iLHist.getIslands();
			// TODO validate islands
			this.iPolicy = Policy.HOT_RANGES;
			policiesParams.put(I_POLICY_HR_RANGES_KEY, islands);
		}
		
	}
	
	public boolean isFlatEnough(Histogram<? extends Number> histogram){
		List<Bin> bins = histogram.getBins();
		for (int i = 1; i < bins.size(); i++) {
			if (bins.get(i).getValue() - bins.get(i - 1).getValue() > FLAT_THRESHOLD){
				return false;
			}
		}
		return true;
	}

	private void calculatePL() {
		//TODO implement smarter! Account cacheUnit size and policies
		log.debug("Calculating P and L for {} cache unit size and {} and {} policies", cacheUnitSize, iPolicy, jPolicy);
		P = jLHist.getMaxRaw().intValue();
		L = iLHist.getMaxRaw();
	}
}
