package edu.nyu.dlib.tipr;

import org.apache.log4j.Logger;
import edu.harvard.hul.ois.mets.*;
import edu.harvard.hul.ois.mets.helper.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unchecked")

public class GenPrAipMets {

	static final String METASUFFIX = ".*_(dc|digiprov|droid|marc|mods|rightsmd|tmd).xml$";
	
	static final String DESC_METASUFFIX = ".*_(dc|pbcore|marc|mods).xml$";
	
	static final String TECH_METASUFFIX = ".*_(droid|tmd).xml$";
	
	static final String NOIDURL = "http://localhost/nd/noidu_testing";
	
	static Logger log = Logger.getLogger(GenPrAipMets.class);

	public static void main(String[] args) {

		if (args.length != 4) {
			System.err
					.println("Usage: java GenPrAipMets <aip id> <aip name> <aip directory> <output file>");
			System.exit(1);
		}

		String aipId = args[0];
		String aipName = args[1];
		String aipDirName = args[2];
		String outputFile = args[3];
		
		
		// String ownerIdPrefix = "info:nyu-dl/x-v1/pr/" + itemId +
		// "/representation/1/";

		try {
			Date now = new Date();

			Mets mets = new Mets();
			mets.setOBJID(aipId);
			mets.setLABEL(aipName);
			mets.setTYPE("");

			MetsHdr metsHdr = new MetsHdr();
			metsHdr.setCREATEDATE(now);
			metsHdr.setLASTMODDATE(now);
			metsHdr.setRECORDSTATUS("Completed");

			Agent agent = new Agent();
			Name name = new Name();
			agent.setROLE(Role.CUSTODIAN);
			agent.setTYPE(Type.ORGANIZATION);
			name.getContent().add(new PCData("NYU-DL"));
			agent.getContent().add(name);
			metsHdr.getContent().add(agent);

			agent = new Agent();
			name = new Name();
			agent.setROLE(Role.CREATOR);
			agent.setTYPE(Type.INDIVIDUAL);
			name.getContent().add(new PCData("Rasch, Rasan"));
			agent.getContent().add(name);
			metsHdr.getContent().add(agent);

			agent = new Agent();
			name = new Name();
			agent.setROLE(Role.DISSEMINATOR);
			agent.setTYPE(Type.ORGANIZATION);
			name.getContent().add(new PCData("NYU-DL"));
			agent.getContent().add(name);
			metsHdr.getContent().add(agent);

			mets.getContent().add(metsHdr);

			java.io.File aipDirectory = new java.io.File(aipDirName);
			if (!aipDirectory.isDirectory()) {
				log.fatal("aip directory " + aipDirectory
						+ " isn't a directory.");
				System.exit(1);
			}
			java.io.File[] aipFiles = aipDirectory.listFiles();
			Arrays.sort(aipFiles);

			ArrayList<java.io.File> metaFiles = new ArrayList<java.io.File>();
			ArrayList<java.io.File> descMetaFiles = new ArrayList<java.io.File>();
			ArrayList<java.io.File> techMetaFiles = new ArrayList<java.io.File>();
			ArrayList<java.io.File> contentFiles = new ArrayList<java.io.File>();
			
			for (int i = 0; i < aipFiles.length; i++) {
				if (aipFiles[i].isHidden() || aipFiles[i].isDirectory()) {
					continue;
				}
				
				String aipFileName = aipFiles[i].getName();
				log.debug(aipFileName);
				if (aipFileName.matches("^(contents|dublin_core.xml|handle)$")) {
					continue;
				}

				if (aipFileName.matches(METASUFFIX)) {
					log.debug("meta file = " + aipFileName);
					metaFiles.add(aipFiles[i]);
					if (aipFileName.matches(DESC_METASUFFIX)) {
						descMetaFiles.add(aipFiles[i]);
					}
					if (aipFileName.matches(TECH_METASUFFIX)) {
						techMetaFiles.add(aipFiles[i]);
					}
				} else {
					contentFiles.add(aipFiles[i]);
				}
			}
			
			
			Pattern patt = Pattern.compile(DESC_METASUFFIX);
			Matcher matcher = patt.matcher("");
			for (int i = 0; i < descMetaFiles.size(); i++) {
				java.io.File descMetaFile = descMetaFiles.get(i);
				String descMetaFileName = descMetaFile.getName();
				
				DmdSec dmdSec = new DmdSec();
				dmdSec.setID(createId("dmd", i));
				MdRef mdRef = new MdRef();
				mdRef.setLOCTYPE(Loctype.URL);
				mdRef.setXlinkHref(descMetaFileName);
				matcher.reset(descMetaFiles.get(i).getName());
				if (matcher.find()) {
					mdRef.setMDTYPE(new Mdtype(matcher.group(1).toUpperCase()));
				} else {
					mdRef.setMDTYPE(Mdtype.OTHER);
					mdRef.setOTHERMDTYPE("UNKNOWN");
				}
				
				dmdSec.getContent().add(mdRef);
				mets.getContent().add(dmdSec);
			}

			AmdSec amdSec = new AmdSec();
			addTechMD(amdSec, techMetaFiles, "tmd");
			mets.getContent().add(amdSec);

			RightsMD rightsMD = new RightsMD();
			rightsMD.setID("rightsmd");
			MdRef mdRef = new MdRef();
			mdRef.setLOCTYPE(Loctype.URL);
			mdRef.setMDTYPE(Mdtype.OTHER);
			mdRef.setOTHERMDTYPE("METSRights");
			mdRef.setXlinkHref(aipId + "_rightsmd.xml");
			rightsMD.getContent().add(mdRef);
			amdSec.getContent().add(rightsMD);

			DigiprovMD digiprovMD = new DigiprovMD();
			digiprovMD.setID("digiprov");
			mdRef = new MdRef();
			mdRef.setLOCTYPE(Loctype.URL);
			mdRef.setMDTYPE(Mdtype.PREMIS);
			mdRef.setXlinkHref(aipId + "_digiprov.xml");
			digiprovMD.getContent().add(mdRef);
			amdSec.getContent().add(digiprovMD);

			FileSec fileSec = new FileSec();

			FileGrp fileGrp;
			fileGrp = createFileGrp(metaFiles, "mdata", "METADATA", "mdata",
					"text/xml", false);
			fileSec.getContent().add(fileGrp);

			fileGrp = createFileGrp(contentFiles, "content", "CONTENT",
					"content", null, true);
			fileSec.getContent().add(fileGrp);

			mets.getContent().add(fileSec);

			StructMap structMap = new StructMap();
			Div div = new Div();

			for (int i = 0; i < contentFiles.size(); i++) {
				Fptr fptr = new Fptr();
				fptr.setFILEID(createId("content", i));
				div.getContent().add(fptr);
			}

			structMap.getContent().add(div);
			mets.getContent().add(structMap);

			mets.validate(new MetsValidator());
			
			mets.write(new MetsWriter(new FileOutputStream(outputFile)));

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static String getChecksum(java.io.File datafile) throws Exception {

		MessageDigest md = MessageDigest.getInstance("MD5");
		FileInputStream fis = new FileInputStream(datafile);
		byte[] dataBytes = new byte[1024];

		int nread = 0;

		while ((nread = fis.read(dataBytes)) != -1) {
			md.update(dataBytes, 0, nread);
		}
		byte[] mdbytes = md.digest();

		// convert the byte to hex format
		StringBuffer sb = new StringBuffer("");
		for (int i = 0; i < mdbytes.length; i++) {
			sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16)
					.substring(1));
		}

		return sb.toString();
	}

	static FileGrp createFileGrp(ArrayList<java.io.File> fileList,
			String grpId, String grpUse, String fileIdPrefix, String mimeType,
			boolean getOriginalName) throws Exception {

		if (fileList.isEmpty()) {
			log.warn(grpId + " file list is empty.");
			return null;
		}

		FileGrp fileGrp = new FileGrp();
		fileGrp.setID(grpId);
		fileGrp.setUSE(grpUse);

		for (int i = 0; i < fileList.size(); i++) {

			java.io.File aipFile = fileList.get(i);
			String aipFileName = aipFile.getName();

			File file = new File();
			file.setID(createId(fileIdPrefix, i));
			file.setCHECKSUMTYPE(Checksumtype.MD5);
			file.setCHECKSUM(getChecksum(aipFile));
			file.setMIMETYPE(mimeType);

			if (getOriginalName) {
				String noid = aipFileName;
				// remove extension from filename
				int dotIndex = noid.lastIndexOf('.');
				if (dotIndex >= 0) {
					noid = noid.substring(0, dotIndex);
				}
				HashMap<String, String> bindings = getBindings(noid);
				file.setOWNERID(bindings.get("sipname"));
			}

			FLocat fLocat = new FLocat();
			fLocat.setLOCTYPE(Loctype.URL);
			fLocat.setXlinkHref(aipFileName);

			file.getContent().add(fLocat);
			fileGrp.getContent().add(file);
		}

		return fileGrp;
	}

	/**
	 * Pad integer with leading zeros.
	 * 
	 * @param value
	 *            integer value to be padded
	 * @param length
	 *            minimum length of padded string
	 * @return zero-padded string
	 */
	static String zeroPad(int value, int length) {
		DecimalFormat df = new DecimalFormat();
		df.setMinimumIntegerDigits(length);
		// df.setGroupingUsed(false);
		return df.format(value);
	}
	
	static String createId(String prefix, int index) {
		return prefix + "_" + zeroPad(index, 3);
	}

	static HashMap<String, String> getBindings(String noid) {
		URL webURL = null;
		String document = "";
		String loc = NOIDURL + "?fetch+" + noid;
		HashMap<String, String> bindings = new HashMap<String, String>();
		try {
			log.debug("*** Loading " + loc + "... ***");
			webURL = new URL(loc);
			BufferedReader is = new BufferedReader(new InputStreamReader(webURL
					.openStream()));
			String line;
			Pattern patt = Pattern.compile("^(\\w+):\\s+(.*)");
			Matcher matcher = patt.matcher("");
			while ((line = is.readLine()) != null) {
				log.debug(line);
				document += line;
				matcher.reset(line);
				if (matcher.find()) {
					String key = URLDecoder.decode(matcher.group(1), "UTF-8");
					String val = URLDecoder.decode(matcher.group(2), "UTF-8");
					log.debug(key + " = " + val);
					bindings.put(key, val);
				}
			}
			is.close();
		} catch (MalformedURLException e) {
			log.error("Load failed: " + e);
		} catch (IOException e) {
			log.error("IOException: " + e);
		}
		return bindings;
	}

	static void addTechMD(AmdSec amdSec, ArrayList<java.io.File> fileList,
			String idPrefix) {

		if (fileList.isEmpty()) {
			return;
		}

		Pattern patt = Pattern.compile(TECH_METASUFFIX);
		Matcher matcher = patt.matcher("");
		for (int i = 0; i < fileList.size(); i++) {
			java.io.File techMDFile = fileList.get(i);
			String techMDFileName = techMDFile.getName();
			
			String mdtype = "UNKNOWN";
			matcher.reset(techMDFileName);
			if (matcher.find()) {
				mdtype = matcher.group(1).toUpperCase();
				if (mdtype.equals("TMD")) {
					mdtype = "NLNZ-PRESMET";
				}
			}
			
			MdRef mdRef = new MdRef();
			mdRef.setMDTYPE(Mdtype.OTHER);
			mdRef.setLOCTYPE(Loctype.URL);
			mdRef.setOTHERMDTYPE(mdtype);
			mdRef.setXlinkHref(techMDFileName);

			TechMD techMD = new TechMD();
			techMD.setID(createId(idPrefix, i));
			techMD.getContent().add(mdRef);
			amdSec.getContent().add(techMD);
		}

	}

}
