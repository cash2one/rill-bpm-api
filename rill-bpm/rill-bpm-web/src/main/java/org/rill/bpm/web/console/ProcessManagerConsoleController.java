package org.rill.bpm.web.console;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngines;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.impl.HistoricProcessInstanceQueryImpl;
import org.activiti.engine.impl.HistoricProcessInstanceQueryProperty;
import org.activiti.engine.impl.ProcessDefinitionQueryImpl;
import org.activiti.engine.impl.ProcessDefinitionQueryProperty;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.util.json.JSONWriter;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.apache.commons.lang.time.DateFormatUtils;
import org.rill.bpm.api.WorkflowOperations;
import org.rill.bpm.api.activiti.ActivitiAccessor;
import org.rill.bpm.web.ScaleoutControllerSupport;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;



@Controller
@RequestMapping("/console")
public class ProcessManagerConsoleController extends ScaleoutControllerSupport {
	
	@RequestMapping(value = { "/" }, method = RequestMethod.GET)
	public synchronized ModelAndView console(HttpServletRequest request,HttpServletResponse response, 
			@RequestParam(value=SCALE_OUT_TARGET, required=false) String fromRequest,
			@CookieValue(value=SCALE_OUT_TARGET, required=false) String fromCookie, ModelMap model) {
		
		String forReturn = null;
		if (fromRequest != null) {
			// Mean need change cookie value
			scaleoutTargetCookie.removeCookie(response);
			scaleoutTargetCookie.addCookie(response, fromRequest);
			
			forReturn = fromRequest;
		} else {
			// First check cookie value
			if (fromCookie != null) {
				forReturn = fromCookie;
			} else {
				// May first access time
				forReturn = ProcessEngines.NAME_DEFAULT;
				scaleoutTargetCookie.addCookie(response, forReturn);
			}
		}
		
		model.addAttribute(SCALE_OUT_TARGET, forReturn);
		return new ModelAndView("/console", "scaleoutTarget", scaleoutTarget);
	}

	@RequestMapping(value = { "/deploy" }, method = RequestMethod.POST)
	public void deploy(HttpServletRequest request,
			HttpServletResponse response, @RequestParam("deployFile") MultipartFile deployFile, 
			@RequestParam("afterDeploy") String afterDeploy) throws Exception {
		
		// FIXME Cross browser issue. IE8 work OK.
		response.setContentType("application/octet-stream; charset=UTF-8");
		try {
			if (deployFile.isEmpty()) {
				throw new IllegalArgumentException("Deployed file is empty." + deployFile.getOriginalFilename());
			}
			StringBuilder deploymentId = new StringBuilder();
			for (Entry<String, ProcessEngine> entry : ProcessEngines.getProcessEngines().entrySet()) {
				
				DeploymentBuilder deploymentBuilder = entry.getValue()
						.getRepositoryService().createDeployment().name(deployFile.getOriginalFilename());
				if(deployFile.getOriginalFilename().toUpperCase().endsWith("ZIP")) {
					deploymentBuilder.addZipInputStream(new ZipInputStream(deployFile.getInputStream()));
				} else if (deployFile.getOriginalFilename().toUpperCase().endsWith("BPMN20\\.XML")) {
					deploymentBuilder.addInputStream(deployFile.getOriginalFilename(), deployFile.getInputStream());
				} else {
					throw new IllegalArgumentException("Support zip/bpmn20.xml file only. " + deployFile.getOriginalFilename());
				}
				deploymentId.append(entry.getKey() + ":" + deploymentBuilder.deploy().getId());
			}
			// Return and call method
			response.getWriter().println("<script>parent." + afterDeploy + "('" + deploymentId  
	                + "', '')</script>"); 
			response.getWriter().flush();
		} catch (Exception e) {
			LOGGER.error("Exception occurred when deploy " + deployFile.getOriginalFilename(), e);
			// Return and call method
			response.getWriter().println("<script type='text/javascript'>parent." + afterDeploy + "('" + -1  
	                + "','" + e.toString() + "');</script>"); 
			response.getWriter().flush();
		}
		
	}
	
	@RequestMapping(value = { "/processDefList" }, method = RequestMethod.GET)
	public void processDefList(HttpServletRequest request,
			final HttpServletResponse response, @CookieValue(value=SCALE_OUT_TARGET, required=true) String fromCookie) throws Exception {
		
		response.setContentType("application/json;charset=UTF-8");
		final PrintWriter out = response.getWriter();
		
		final Integer page = Integer.parseInt(request.getParameter("page"));
		final Integer rows = Integer.parseInt(request.getParameter("rows"));
		final boolean sord = "asc".equals(request.getParameter("sord")) ? true : false;
		final ProcessDefinitionQueryProperty pdqp = ProcessDefinitionQueryProperty.findByName(request.getParameter("sidx")) == null ? 
				ProcessDefinitionQueryProperty.PROCESS_DEFINITION_KEY : ProcessDefinitionQueryProperty.findByName(request.getParameter("sidx"));
		// FIXME: MENGRAN. Need wrap a search operations
		final String searchKey = "true".equals(request.getParameter("_search")) ? request.getParameter("PD.KEY_") : null;
		
		WorkflowOperations workflowOperations = scaleoutTarget.get(fromCookie);
		ActivitiAccessor activitiAccessor = ActivitiAccessor.retrieveActivitiAccessorImpl(workflowOperations, ActivitiAccessor.class);
		activitiAccessor.runExtraCommand(new Command<Void>() {

			@Override
			public Void execute(CommandContext commandContext) {
				
				ProcessDefinitionQuery pdq = new ProcessDefinitionQueryImpl(commandContext).orderBy(pdqp);
				if (sord) {
					pdq.asc();
				} else {
					pdq.desc();
				}
				// Search operation 
				if (searchKey != null) {
					pdq.processDefinitionKey(searchKey);
				}
				List<ProcessDefinition> processDefList = pdq.listPage((page - 1) * rows, rows);
				long totalCnt = pdq.count();
				
				List<Map<String, Object>> processDefMap = new ArrayList<Map<String,Object>>(processDefList.size());
				for (int i = 0; i < processDefList.size(); i++) {
					Map<String, Object> element = new LinkedHashMap<String, Object>();
					element.put("id", processDefList.get(i).getId());
					String[] peerProcessDef = new String[6];
					// Key
					peerProcessDef[0] = processDefList.get(i).getKey();
					// Running
					Long runningCnt = new HistoricProcessInstanceQueryImpl(commandContext).processDefinitionId(processDefList.get(i).getId()).unfinished().count();
					peerProcessDef[1] = runningCnt + "";
					// End
					Long endCnt = new HistoricProcessInstanceQueryImpl(commandContext).processDefinitionId(processDefList.get(i).getId()).finished().count();
					peerProcessDef[2] = endCnt + "";
					// Version
					peerProcessDef[3] = processDefList.get(i).getVersion() + "";
					// Deploy Time
					Deployment deployment = commandContext.getDeploymentManager().findDeploymentById(processDefList.get(i).getDeploymentId());
					peerProcessDef[4] = DateFormatUtils.format(deployment.getDeploymentTime(), "yyyy-MM-dd HH:mm:ss");
					// Command
					// FIXME Only latest process definition
					ProcessDefinitionEntity latestProcessDefinition = commandContext.getProcessDefinitionManager().findLatestProcessDefinitionByKey(processDefList.get(i).getKey());
					peerProcessDef[5] = processDefList.get(i).getId().equals(latestProcessDefinition.getId()) ? processDefList.get(i).getId() : "";
					element.put("cell", peerProcessDef);
					processDefMap.add(element);
				}
				
				JSONWriter jsonWriter = new JSONWriter(out);
				jsonWriter
					.object()
					.key("total")
					.value((totalCnt / rows) + 1)
					.key("page")
					.value(page)
					.key("records")
					.value(totalCnt)
					.key("invdata")
					.value(processDefMap)
					.endObject();
				out.flush();
				
				return null;
			}
			
		});
		
	}
	
	@RequestMapping(value = { "/processInstanceList/{running}/{processDefinitionId}" }, method = RequestMethod.GET)
	public void processInstanceList(@PathVariable(value="running") final boolean running, 
			@PathVariable(value="processDefinitionId") final String processDefinitionId, HttpServletRequest request,
			final HttpServletResponse response, @CookieValue(value=SCALE_OUT_TARGET, required=true) String fromCookie) throws Exception {
		
		response.setContentType("application/json;charset=UTF-8");
		final PrintWriter out = response.getWriter();
		
		final Integer page = Integer.parseInt(request.getParameter("page"));
		final Integer rows = Integer.parseInt(request.getParameter("rows"));
		final boolean sord = "asc".equals(request.getParameter("sord")) ? true : false;
		final HistoricProcessInstanceQueryProperty hpiqp = HistoricProcessInstanceQueryProperty.findByName(request.getParameter("sidx")) == null ? 
				HistoricProcessInstanceQueryProperty.PROCESS_INSTANCE_ID_ : HistoricProcessInstanceQueryProperty.findByName(request.getParameter("sidx"));
		// FIXME: MENGRAN. Need wrap a search operations
		final String searchKey = "true".equals(request.getParameter("_search")) ? request.getParameter("BUSINESS_KEY_") : null;
		
		WorkflowOperations workflowOperations = scaleoutTarget.get(fromCookie);
		ActivitiAccessor activitiAccessor = ActivitiAccessor.retrieveActivitiAccessorImpl(workflowOperations, ActivitiAccessor.class);
		activitiAccessor.runExtraCommand(new Command<Void>() {

			@Override
			public Void execute(CommandContext commandContext) {
				
				HistoricProcessInstanceQuery hpiq = new HistoricProcessInstanceQueryImpl(commandContext).orderBy(hpiqp);
				if (sord) {
					hpiq.asc();
				} else {
					hpiq.desc();
				}
				if (!running) {
					hpiq.finished();
				} else {
					hpiq.unfinished();
				}
				if (!"-1".equals(processDefinitionId)) {
					 hpiq.processDefinitionId(processDefinitionId);
				}
				// Search operation 
				if (searchKey != null) {
					hpiq.processInstanceBusinessKey(searchKey);
				}
				List<HistoricProcessInstance> processInstanceList = hpiq.listPage((page - 1) * rows, rows);
				long totalCnt = hpiq.count();
				
				List<Map<String, Object>> processInstanceMap = new ArrayList<Map<String,Object>>(processInstanceList.size());
				for (int i = 0; i < processInstanceList.size(); i++) {
					Map<String, Object> element = new LinkedHashMap<String, Object>();
					element.put("id", processInstanceList.get(i).getId());
					String[] peerProcessDef = new String[6];
					// Process instance id
					peerProcessDef[0] = processInstanceList.get(i).getId();
					// Business key
					peerProcessDef[1] = processInstanceList.get(i).getBusinessKey();
					// Process definition key
					ProcessDefinition pd = new ProcessDefinitionQueryImpl(commandContext).processDefinitionId(processInstanceList.get(i).getProcessDefinitionId()).singleResult();
					peerProcessDef[2] = pd.getKey();
					// Start user
					peerProcessDef[3] = processInstanceList.get(i).getStartUserId();
					// Start time
					peerProcessDef[4] = DateFormatUtils.format(processInstanceList.get(i).getStartTime(), "yyyy-MM-dd HH:mm:ss");
					// End time
					peerProcessDef[5] = processInstanceList.get(i).getEndTime() == null ? ""
							: DateFormatUtils.format(processInstanceList.get(i).getEndTime(), "yyyy-MM-dd HH:mm:ss");
					element.put("cell", peerProcessDef);
					processInstanceMap.add(element);
				}
				
				JSONWriter jsonWriter = new JSONWriter(out);
				jsonWriter
					.object()
					.key("total")
					.value((totalCnt / rows) + 1)
					.key("page")
					.value(page)
					.key("records")
					.value(totalCnt)
					.key("invdata")
					.value(processInstanceMap)
					.endObject();
				out.flush();
				
				return null;
			}
			
		});
	}
}
