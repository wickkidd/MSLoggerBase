import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class Normaliser
{
	enum Section
	{
		None, Header, Expressions, Gauges, Logs, FrontPage, Constants
	};

	private static Pattern				bits			= Pattern
																.compile("(\\w*)\\s*=\\s*bits,\\s*(.*),\\s*(.*),\\s*\\[(\\d):(\\d)\\].*");
	private static Pattern				scalar			= Pattern
																.compile("(\\w*)\\s*=\\s*scalar\\s*,\\s*([U|S]\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(.*,.*)");
	private static Pattern				expr			= Pattern.compile("(\\w*)\\s*=\\s*\\{\\s*(.*)\\s*\\}.*");
	private static Pattern				ternary			= Pattern.compile("(.*?)\\?(.*)");
	private static Pattern				log				= Pattern.compile("\\s*entry\\s*=\\s*(\\w+)\\s*,\\s*\"(.*)\",.*");
	private static Pattern				binary			= Pattern.compile("(.*)0b([01]{8})(.*)");
	private static Pattern				gauge			= Pattern
																.compile("\\s*(.*?)\\s*=\\s*(.*?)\\s*,\\s*\"(.*?)\"\\s*,\\s*\"(.*?)\"\\s*,\\s*(.*?)\\s*,\\s*(.*?)\\s*,\\s*(.*?)\\s*,\\s*(.*?)\\s*,\\s*(.*?)\\s*,\\s*(.*?)\\s*,\\s*(.*?)\\s*,\\s*(.*)");

	private static Pattern				queryCommand	= Pattern.compile("\\s*queryCommand\\s*=\\s*\"(.*)\".*");
	private static Pattern				signature		= Pattern.compile("\\s*signature\\s*=\\s*\"(.*)\".*");
	private static Pattern				ochGetCommand	= Pattern.compile("\\s*ochGetCommand\\s*=\\s*\"(.*)\".*");
	private static Pattern				ochBlockSize	= Pattern.compile("\\s*ochBlockSize\\s*=\\s*(\\d*).*");
	private static Pattern				defaultGauge	= Pattern.compile("\\s*gauge\\d\\s*=\\s*(\\w*)");
	private static Pattern				page			= Pattern.compile("\\s*page\\s*=\\s*(\\d*)");
	private static Pattern				constant		= Pattern
																.compile("\\s*(\\w*)\\s*=\\s*(\\w*)\\s*,\\s*(.*?)\\s*,\\s*(\\d*)\\s*,\\s*\\\"(.*)\\\"\\s*,\\s*([-+]?\\d*.?\\d*)\\s*,\\s*([-+]?\\d*\\.?\\d*)\\s*,\\s*([-+]?\\d*\\.?\\d*)\\s*,\\s*([-+]?\\d*\\.?\\d*)\\s*,\\s*([-+]?\\d*\\.?\\d*)");
	private static List<String>			runtime			= new ArrayList<String>();
	private static List<String>			logHeader		= new ArrayList<String>();
	private static List<String>			logRecord		= new ArrayList<String>();
	private static List<String>			gaugeDef		= new ArrayList<String>();
	private static Map<String, String>	runtimeVars;
	private static Map<String, String>	evalVars;
	private static Map<String, String>	constantVars;
	private static Set<String>			flags;
	private static String				fingerprintSource;
	private static ArrayList<String>	gaugeDoc;
	private static String				signatureStr;
	private static String				queryCommandStr;
	private static String				ochGetCommandStr;
	private static String				ochBlockSizeStr;
	private static ArrayList<String>	defaultGauges;
	private static int					currentPage		= 0;
	private static ArrayList<Constant>	constants;

	/**
	 * @param args
	 * @throws FileNotFoundException
	 */
	public static void main(String[] args) throws IOException
	{
		for (String filename : args)
		{
			process(filename);
		}
	}

	private static void process(String filename) throws IOException
	{
		signatureStr = "";
		queryCommandStr = "";
		runtime = new ArrayList<String>();
		logHeader = new ArrayList<String>();
		logRecord = new ArrayList<String>();
		runtimeVars = new HashMap<String, String>();
		evalVars = new HashMap<String, String>();
		constantVars = new HashMap<String, String>();
		constants=new ArrayList<Constant>();
		flags = new HashSet<String>();
		gaugeDef = new ArrayList<String>();
		gaugeDoc = new ArrayList<String>();
		defaultGauges = new ArrayList<String>();
		fingerprintSource = "";
		currentPage = 0;
		constants = new ArrayList<Constant>();
		File f = new File(filename);
		if (f.isDirectory())
			return;
		String className = f.getName();
		Section currentSection = Section.None;

		BufferedReader br = new BufferedReader(new FileReader(f));

		String line;

		while ((line = br.readLine()) != null)
		{
			if (line.trim().equals("[MegaTune]"))
			{
				currentSection = Section.Header;
				continue;
			}
			else if (line.trim().equals("[OutputChannels]"))
			{
				currentSection = Section.Expressions;
				continue;
			}
			else if (line.trim().equals("[Datalog]"))
			{
				currentSection = Section.Logs;
				continue;

			}
			else if (line.trim().equals("[GaugeConfigurations]"))
			{
				currentSection = Section.Gauges;
				continue;

			}
			else if (line.trim().equals("[FrontPage]"))
			{
				currentSection = Section.FrontPage;
			}
			else if (line.trim().equals("[Constants]"))
			{
				currentSection = Section.Constants;
			}
			else if (line.trim().startsWith("["))
			{
				currentSection = Section.None;
				continue;

			}
			switch (currentSection)
			{
			case Expressions:
				processExpr(line);
				break;
			case Logs:
				processLogEntry(line);
				break;
			case Gauges:
				processGaugeEntry(line);
				break;
			case Header:
				processHeader(line);
				break;
			case FrontPage:
				processFrontPage(line);
				break;
			case Constants:
				processConstants(line);
				break;
			}

		}
		writeFile(f.getParent(), className);
	}

	private static void processConstants(String line)
	{
		line+="; junk";
		line = StringUtils.trim(line).split(";")[0];
		if (StringUtils.isEmpty(line))
		{
			return;
		}
		Matcher pageM = page.matcher(line);
		if (pageM.matches())
		{
			currentPage = Integer.parseInt(pageM.group(1));
			return;
		}
		Matcher constantM = constant.matcher(line);
		if (constantM.matches())
		{
			String name = constantM.group(1);
			String classtype = constantM.group(2);
			String type = constantM.group(3);
			int offset = Integer.parseInt(constantM.group(4));
			String shape = constantM.group(5);
			String units = constantM.group(6);
			double scale = Double.parseDouble(constantM.group(7));
			double translate = Double.parseDouble(constantM.group(8));
			double low = Double.parseDouble(constantM.group(9));
			double high = Double.parseDouble(constantM.group(10));
			int digits = (int) Double.parseDouble(constantM.group(11));

			if ("bits".equals(classtype) || "scalar".equals(classtype))
			{
				Constant c = new Constant(currentPage, name, classtype, type, offset, shape, units, scale, translate, low, high,
						digits);

				if (scale == 1.0)
				{
					constantVars.put(name, "int");
				}
				else
				{
					constantVars.put(name, "double");
				}
				constants.add(c);
			}
		}
		else if (line.startsWith("#"))
		{
			String preproc = (processPreprocessor(line));
			Constant c = new Constant(currentPage, preproc, "", "PREPROC", 0, "", "", 0, 0, 0, 0, 0);
			constants.add(c);
		}
	}

	private static void processFrontPage(String line)
	{
		Matcher dgM = defaultGauge.matcher(line);
		if (dgM.matches())
		{
			defaultGauges.add(dgM.group(1));
		}
	}

	private static void processHeader(String line)
	{
		Matcher queryM = queryCommand.matcher(line);
		if (queryM.matches())
		{
			queryCommandStr = "byte[] queryCommand=new byte[]{'" + queryM.group(1) + "'};";
			return;
		}

		Matcher sigM = signature.matcher(line);
		if (sigM.matches())
		{
			String tmpsig = sigM.group(1);
			if (line.contains("null"))
			{
				tmpsig += "\\0";
			}
			signatureStr = "String signature=\"" + tmpsig + "\";";
		}
	}

	private static void processGaugeEntry(String line)
	{
		Matcher m = gauge.matcher(line);
		if (m.matches())
		{
			String name = m.group(1);
			String channel = m.group(2);
			String title = m.group(3);
			String units = m.group(4);
			String lo = m.group(5);
			String hi = m.group(6);
			String loD = m.group(7);
			String loW = m.group(8);
			String hiW = m.group(9);
			String hiD = m.group(10);
			String vd = m.group(11);
			String ld = m.group(12);

			String g = String.format(
					"GaugeRegister.INSTANCE.addGauge(new GaugeDetails(\"%s\",\"%s\",%s,\"%s\",\"%s\",%s,%s,%s,%s,%s,%s,%s,%s,0));",
					name, channel, channel, title, units, lo, hi, loD, loW, hiW, hiD, vd, ld);
			String gd = String
					.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>",
							name, channel, title, units, lo, hi, loD, loW, hiW, hiD, vd, ld);
			gaugeDoc.add(gd);
			gaugeDef.add(g);

		}
		else if (line.startsWith("#"))
		{
			gaugeDef.add(processPreprocessor(line));
			gaugeDoc.add(String.format("<tr><td colspan=\"12\" id=\"preprocessor\">%s</td></tr>", line));
		}

	}

	private static void writeFile(String path, String className) throws IOException
	{
		className = StringUtils.capitalize(className);
		className = StringUtils.remove(className, ".");
		className = StringUtils.remove(className, "ini");
		className = StringUtils.replace(className, " ", "_");
		className = StringUtils.replace(className, "-", "_");
		className = "ZZ" + className;
		String classFile = path + "/gen_src/" + className + ".java";
		System.out.println("Writing to " + classFile);
		PrintWriter writer = new PrintWriter(new FileWriter(classFile));

		writer.println("public class " + className + " extends Megasquirt\n{");
		writer.println(queryCommandStr);
		writer.println(signatureStr);
		writer.println(ochGetCommandStr);
		writer.println(ochBlockSizeStr);
		writer.println("private Set<String> sigs = new HashSet<String>(Arrays.asList(new String[] { signature }));");
		writer.println("private String[] defaultGauges = {");
		boolean first = true;
		for (String dg : defaultGauges)
		{
			if (!first)
				writer.print(",");
			first = false;
			writer.println("\"" + dg + "\"");
		}
		writer.println("};");

		writer.println("//Flags");
		for (String name : flags)
		{
			writer.println("boolean " + name + ";");
		}
		writer.println("//Runtime vars");
		for (String name : runtimeVars.keySet())
		{
			writer.println(runtimeVars.get(name) + " " + name + ";");
		}
		writer.println("\n//eval vars");
		for (String name : evalVars.keySet())
		{
			writer.println(evalVars.get(name) + " " + name + ";");
		}
		writer.println("\n//Constants");
		for (String name : constantVars.keySet())
		{
			writer.println(constantVars.get(name) + " " + name + ";");
		}
		writer.println("\n");
		writer.println("	@Override");
		writer.println("	public void calculate(byte[] ochBuffer) throws IOException");
		writer.println("{");
		for (String defn : runtime)
		{
			writer.println(defn);
			// System.out.println(defn);
		}
		writer.println("}");
		writer.println("@Override");
		writer.println("public String getLogHeader()");
		writer.println("{");
		writer.println("	StringBuffer b = new StringBuffer();");
		for (String header : logHeader)
		{
			writer.println(header);
		}
		writer.println("b.append(MSUtils.getLocationLogHeader());");
		writer.println("    return b.toString();\n}\n");
		writer.println("@Override");
		writer.println("public String getLogRow()");
		writer.println("{");
		writer.println("	StringBuffer b = new StringBuffer();");

		for (String record : logRecord)
		{
			writer.println(record);
		}
		writer.println("b.append(MSUtils.getLocationLogRow());");
		writer.println("    return b.toString();\n}\n");
		writer.println("@Override");
		writer.println("public void initGauges()");
		writer.println("{");
		for (String gauge : gaugeDef)
		{
			writer.println(gauge);
		}
		writer.println("\n}\n");

		writer.println("/*");
		for (String gauge : gaugeDoc)
		{
			writer.println(gauge);
		}

		writer.println("*/");

		outputOverrides(writer);
		outputLoadConstants(writer);
		writer.println("\n}\n");

		writer.close();
		System.out.println(getFingerprint() + " : " + className);
	}

	private static void outputLoadConstants(PrintWriter writer)
	{
		writer.println("@Override");
		writer.println("public void loadConstants(boolean simulated)");
		writer.println("{");

		int pageNo = 0;
		for(Constant c : constants)
		{
			if(c.getPage() != pageNo)
			{
				pageNo=c.getPage();
				outputLoadPage(pageNo,writer);
				//getScalar(String bufferName,String name, String dataType, String offset, String scale, String numOffset)
				if(!"PREPROC".equals(c.getType()))
				{
				String def = getScalar("pageBuffer",c.getName(),c.getType(),""+c.getOffset(),""+c.getScale(),""+c.getTranslate());
				writer.println(def);
				}
				else
				{
					writer.println(c.getName());
				}
			}
		}
		
		
		
		writer.println("}");
	}

	private static void outputLoadPage(int pageNo, PrintWriter writer)
	{
		writer.println("loadPage("+pageNo+");");
		
	}

	private static void outputOverrides(PrintWriter writer)
	{
		String overrides = "@Override\n" + "public Set<String> getSignature()\n" + "{\n" + "    return sigs;\n" + "}\n"
				+ "@Override\n" + "public byte[] getOchCommand()\n" + "{\n" + "    \n" + "    return this.ochGetCommand;\n" + "}\n"
				+

				"@Override\n" + "public byte[] getSigCommand()\n" + "{\n" + "    return this.queryCommand;\n" + "}\n" +

				"@Override\n" + "public int getBlockSize()\n" + "{\n" + "    return this.ochBlockSize;\n" + "}\n" +

				"@Override\n" + "public int getSigSize()\n" + "{\n" + "    return signature.length();\n" + "}\n" +

				"@Override\n" + "public int getPageActivationDelay()\n" + "{\n" + "    return 100;\n" + "}\n" +

				"@Override\n" + "public int getInterWriteDelay()\n" + "{\n" + "    return 10;\n" + "}\n" +

				"@Override\n" + "public int getCurrentTPS()\n" + "{\n" + "   \n" + "    return tpsADC;\n" + "}\n" +

				"@Override\n" + "public String[] defaultGauges()\n" + "{\n" + "    return defaultGauges;\n" + "}\n";

		writer.println(overrides);
	}

	private static String getFingerprint()
	{
		StringBuffer b = new StringBuffer();
		try
		{
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] array = md.digest(fingerprintSource.getBytes());
			for (int i = 0; i < array.length; i++)
			{
				b.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
			}
		}
		catch (NoSuchAlgorithmException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return b.toString();
	}

	private static void processLogEntry(String line)
	{
		Matcher logM = log.matcher(line);
		if (logM.matches())
		{
			String header = logM.group(2);
			String variable = logM.group(1);
			if ("double".equals(runtimeVars.get(variable)))
			{
				variable = "round(" + variable + ")";
			}
			logHeader.add("b.append(\"" + header + "\").append(\"\\t\");");
			logRecord.add("b.append(" + variable + ").append(\"\\t\");");
		}
		else if (line.startsWith("#"))
		{
			String directive = processPreprocessor(line);
			logHeader.add(directive);
			logRecord.add(directive);
		}
	}

	private static void processExpr(String line)
	{
		String definition = null;
		line = StringUtils.trim(line).split(";")[0];
		if (StringUtils.isEmpty(line))
		{
			return;
		}
		line = StringUtils.replace(line, "timeNow", "timeNow()");
		Matcher bitsM = bits.matcher(line);
		Matcher scalarM = scalar.matcher(line);
		Matcher exprM = expr.matcher(line);
		Matcher ochGetCommandM = ochGetCommand.matcher(line);
		Matcher ochBlockSizeM = ochBlockSize.matcher(line);
		if (bitsM.matches())
		{
			String name = bitsM.group(1);
			String offset = bitsM.group(3);
			String start = bitsM.group(4);
			String end = bitsM.group(5);
			definition = (name + " = MSUtils.getBits(ochBuffer," + offset + "," + start + "," + end + ");");
			runtime.add(definition);
			runtimeVars.put(name, "int");
		}
		else if (scalarM.matches())
		{
			String name = scalarM.group(1);
			String dataType = scalarM.group(2);
			String offset = scalarM.group(3);
			String scalingRaw = scalarM.group(4);
			String[] scaling = scalingRaw.split(",");
			String scale = scaling[1].trim();
			String numOffset = scaling[2].trim();
			if (Double.parseDouble(scale) != 1)
			{
				runtimeVars.put(name, "double");
			}
			else
			{
				runtimeVars.put(name, "int");
			}
			definition = getScalar("ochBuffer",name, dataType, offset, scale, numOffset);
			fingerprintSource += definition;
			runtime.add(definition);
		}
		else if (exprM.matches())
		{
			String name = exprM.group(1);
			String expression = deBinary(exprM.group(2).trim());
			Matcher ternaryM = ternary.matcher(expression);
			if (ternaryM.matches())
			{
				// System.out.println("BEFORE : " + expression);
				String test = ternaryM.group(1);
				String values = ternaryM.group(2);
				if (StringUtils.containsAny(test, "<>!="))
				{
					expression = "(" + test + ") ? " + values;
				}
				else
				{
					expression = "((" + test + ") != 0 ) ? " + values;
				}
				// System.out.println("AFTER  : " + expression + "\n");
			}
			definition = name + " = (" + expression + ");";
			runtime.add(definition);
			if (isFloatingExpression(expression))
			{
				evalVars.put(name, "double");
			}
			else
			{
				evalVars.put(name, "int");
			}
		}
		else if (ochGetCommandM.matches())
		{
			ochGetCommandStr = "byte [] ochGetCommand = new byte[]{'" + ochGetCommandM.group(1) + "'};";
		}
		else if (ochBlockSizeM.matches())
		{
			ochBlockSizeStr = "int ochBlockSize = " + ochBlockSizeM.group(1) + ";";
		}
		else if (line.startsWith("#"))
		{
			runtime.add(processPreprocessor(line));
		}
		else
		{
			System.out.println(line);
		}
	}

	private static String deBinary(String group)
	{
		Matcher binNumber = binary.matcher(group);
		if (!binNumber.matches())
		{
			return group;
		}
		else
		{
			String binNum = binNumber.group(2);
			int num = Integer.parseInt(binNum, 2);
			String expr = binNumber.group(1) + num + binNumber.group(3);
			return deBinary(expr);
		}
	}

	private static boolean isFloatingExpression(String expression)
	{
		return expression.contains(".");
	}

	private static String processPreprocessor(String line)
	{
		String filtered;
		boolean stripped = false;

		filtered = line.replace("  ", " ");
		stripped = filtered.equals(line);
		while (!stripped)
		{
			line = filtered;
			filtered = line.replace("  ", " ");
			stripped = filtered.equals(line);
		}
		String[] components = line.split(" ");
		if (components[0].equals("#if"))
		{
			flags.add(components[1]);
			return ("if (" + components[1] + ")\n{");
		}
		if (components[0].equals("#elif"))
		{
			flags.add(components[1]);
			return ("}\nelse if (" + components[1] + ")\n{");
		}
		if (components[0].equals("#else"))
		{
			return ("}\nelse\n{");
		}
		if (components[0].equals("#endif"))
		{
			return ("}");
		}

		return "";
	}

	private static String getScalar(String bufferName,String name, String dataType, String offset, String scale, String numOffset)
	{
		String definition = name + " = (" + runtimeVars.get(name) + ")((MSUtils.get";
		if (dataType.startsWith("S"))
		{
			definition += "Signed";
		}
		int size = Integer.parseInt(dataType.substring(1));
		switch (size)
		{
		case 8:
			definition += "Byte";
			break;
		case 16:
			definition += "Word";
			break;
		case 32:
			definition += "Long";
			break;
		default:
			definition += dataType;
			break;
		}
		definition += "("+bufferName+"," + offset + ") + " + numOffset + ") * " + scale + ");";
		return definition;
	}

}
