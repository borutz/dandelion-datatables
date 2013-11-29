/*
 * [The "BSD licence"]
 * Copyright (c) 2012 Dandelion
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 3. Neither the name of Dandelion nor the names of its contributors 
 * may be used to endorse or promote products derived from this software 
 * without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.dandelion.datatables.core.generator;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dandelion.core.utils.StringUtils;
import com.github.dandelion.datatables.core.asset.ExtraConf;
import com.github.dandelion.datatables.core.asset.ExtraFile;
import com.github.dandelion.datatables.core.asset.JsResource;
import com.github.dandelion.datatables.core.exception.ExtraFileNotFoundException;
import com.github.dandelion.datatables.core.exception.WebResourceGenerationException;
import com.github.dandelion.datatables.core.export.ExportManager;
import com.github.dandelion.datatables.core.extension.ExtensionLoader;
import com.github.dandelion.datatables.core.generator.configuration.DatatablesGenerator;
import com.github.dandelion.datatables.core.html.ExtraHtml;
import com.github.dandelion.datatables.core.html.HtmlTable;
import com.github.dandelion.datatables.core.util.FileUtils;
import com.github.dandelion.datatables.core.util.JsonIndentingWriter;

/**
 * <p>
 * Class in charge of web resources generation.
 * 
 * <p>
 * The generated JSON (DataTables configuration) is pretty printed using a
 * custom writer written by Elad Tabak.
 * 
 * @author Thibault Duchateau
 */
public class WebResourceGenerator {

	// Logger
	private static Logger logger = LoggerFactory.getLogger(WebResourceGenerator.class);

	// Custom writer used to pretty print JSON
	private Writer writer = new JsonIndentingWriter();

	private HtmlTable table;
	
	/**
	 * The DataTables configuration generator.
	 */
	private static DatatablesGenerator configGenerator;
	
	/**
	 * All managers used to generate web resources.
	 */
	private ExportManager exportManager = new ExportManager();

	public WebResourceGenerator(HtmlTable table){
		this.table = table;
	}
	
	/**
	 * <p>
	 * Main method which generated the web resources (js and css files).
	 * 
	 * @param pageContext
	 *            Context of the servlet.
	 * @param table
	 *            Table from which the configuration is extracted.
	 * @return A string corresponding to the Javascript code to return to the
	 *         JSP.
	 */
	public JsResource generateWebResources() {

		/**
		 * Main configuration file building
		 */
		JsResource mainJsFile = new JsResource();
		mainJsFile.setTableId(table.getId());
		
		/**
		 * Export management
		 */
		if (table.getTableConfiguration().isExportable()) {
			exportManager.exportManagement(table, mainJsFile);
		}
		
		// Init the "configuration" map with the table informations
		// The configuration may be updated depending on the user's choices
		configGenerator = new DatatablesGenerator();
		Map<String, Object> mainConf = configGenerator.generateConfig(table);

		/**
		 * Extra files management
		 */
		if (table.getTableConfiguration().getExtraFiles() != null && !table.getTableConfiguration().getExtraFiles().isEmpty()) {
			extraFileManagement(mainJsFile, table);
		}

		/**
		 * Extension loading
		 */
		ExtensionLoader extensionLoader = new ExtensionLoader(table);
		extensionLoader.loadExtensions(mainJsFile, mainConf);
		
		/**
		 * Extra configuration management
		 */
		if(table.getTableConfiguration().getExtraConfs() != null){
			extraConfManagement(mainJsFile, mainConf, table);			
		}

		/**
		 * Main configuration generation
		 */
		// Allways pretty prints the JSON
		try {
			JSONValue.writeJSONString(mainConf, writer);
			mainJsFile.appendToDataTablesConf(writer.toString());
		} catch (IOException e) {
			throw new WebResourceGenerationException("Unable to generate the JSON configuration", e);
		}

		/**
		 * Extra HTML
		 */
		if(table.getTableConfiguration().getExtraHtmlSnippets() != null && !table.getTableConfiguration().getExtraHtmlSnippets().isEmpty()){
			extraHtmlManagement(mainJsFile);
		}
		
		/**
		 * Table display
		 */
		if(StringUtils.isNotBlank(table.getTableConfiguration().getFeatureAppear())){
			
			if("block".equals(table.getTableConfiguration().getFeatureAppear())){
				mainJsFile.appendToBeforeEndDocumentReady("$('#" + table.getId() + "').show();");			
			}
			else{
				if(StringUtils.isNotBlank(table.getTableConfiguration().getFeatureAppearDuration())){
					mainJsFile.appendToBeforeEndDocumentReady("$('#" + table.getId() + "').fadeIn(" + table.getTableConfiguration().getFeatureAppearDuration() + ");");
				}
				else{
					mainJsFile.appendToBeforeEndDocumentReady("$('#" + table.getId() + "').fadeIn();");
				}
			}
		}
		
//		webResources.setMainJsFile(mainJsFile);

		return mainJsFile;
	}

	private void extraHtmlManagement(JsResource mainJsFile) {
		for(ExtraHtml group : table.getTableConfiguration().getExtraHtmlSnippets()){
			StringBuilder jsGroup = new StringBuilder();

			jsGroup.append("$.fn.dataTableExt.aoFeatures.push( {");
			jsGroup.append("\"fnInit\": function( oDTSettings ) {");
			jsGroup.append("var container = document.createElement('" + group.getContainer() + "');");
			
			if(StringUtils.isNotBlank(group.getCssClass())){
				jsGroup.append("$(container).attr('class', '" + group.getCssClass() + "');");
			}
			if(StringUtils.isNotBlank(group.getCssStyle())){
				jsGroup.append("$(container).attr('style', '" + group.getCssStyle() + "');");
			}
			
			jsGroup.append("$(container).html('" + group.getContent().replaceAll("'", "&quot;") + "');");
			jsGroup.append("return container;");
			jsGroup.append("},");
			jsGroup.append("\"cFeature\": \"" + group.getUid() + "\",");
			jsGroup.append("\"sFeature\": \"" + "Group" + group.getUid() + "\"");
			jsGroup.append("} );");
			
			mainJsFile.appendToAfterStartDocumentReady(jsGroup.toString());
		}
	}

	
	/**
	 * If extraFile tag have been added, its content must be extracted and merge
	 * to the main js file.
	 * 
	 * @param mainFile
	 *            The resource to update with extraFiles.
	 * @param table
	 *            The HTML tale.
	 */
	private void extraFileManagement(JsResource mainFile, HtmlTable table) {

		logger.info("Extra files found");

		for (ExtraFile file : table.getTableConfiguration().getExtraFiles()) {
			try {

				switch (file.getInsert()) {
				case BEFOREALL:
					mainFile.appendToBeforeAll(FileUtils.getFileContentFromWebapp(file.getSrc()));
					break;

				case AFTERSTARTDOCUMENTREADY:
					mainFile.appendToAfterStartDocumentReady(FileUtils.getFileContentFromWebapp(file.getSrc()));
					break;

				case BEFOREENDDOCUMENTREADY:
					mainFile.appendToBeforeEndDocumentReady(FileUtils.getFileContentFromWebapp(file.getSrc()));
					break;

				case AFTERALL:
					mainFile.appendToAfterAll(FileUtils.getFileContentFromWebapp(file.getSrc()));
					break;
					
				case BEFORESTARTDOCUMENTREADY:
					mainFile.appendToBeforeStartDocumentReady(FileUtils.getFileContentFromWebapp(file.getSrc()));
					break;
				}
				
			} catch (IOException e) {
				StringBuilder msg = new StringBuilder("Unable to load the extra file ");
				msg.append(file.getSrc());
				throw new ExtraFileNotFoundException(msg.toString());
			}
		}
	}

	/**
	 * Generates a jQuery AJAX call to be able to merge the server-generated
	 * DataTables configuration with the configuration stored in extraConf
	 * files. <br />
	 * Warning : this is a temporary method. The goal is to be able to generate
	 * the entire configuration server-side.
	 * 
	 * @param mainConf
	 * @param table
	 */
	private void extraConfManagement(JsResource mainJsFile, Map<String, Object> mainConf,
			HtmlTable table) {

		for (ExtraConf conf : table.getTableConfiguration().getExtraConfs()) {
			StringBuilder extaConf = new StringBuilder();
			extaConf.append("$.ajax({url:\"");
			extaConf.append(conf.getSrc());
			extaConf.append("\",dataType: \"text\",type: \"GET\", async: false, success: function(extraProperties, xhr, response) {");
			extaConf.append("$.extend(true, oTable_");
			extaConf.append(table.getId());
			extaConf.append("_params, eval('(' + extraProperties + ')'));");
			extaConf.append("}, error : function(jqXHR, textStatus, errorThrown){");
			extaConf.append("console.log(textStatus);");
			extaConf.append("console.log(errorThrown);");
			extaConf.append("}});");
			extaConf.append("console.log(oTable_" + table.getId() + "_params);");
			mainJsFile.appendToBeforeStartDocumentReady(extaConf.toString());
		}
	}
}