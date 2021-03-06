/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rill.bpm.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.rill.bpm.api.exception.ProcessException;
import org.rill.bpm.api.scaleout.ScaleoutKeySource;
import org.rill.bpm.api.scaleout.ScaleoutKeySource.RETRIEVE_TYPE;
import org.springframework.util.Assert;

import com.thoughtworks.xstream.XStream;

/**
 * Workflow operations abstraction, it define all interface of actual engine.
 * 
 * @author mengran
 */
public interface WorkflowOperations {
    
    enum TaskInformations {

        PROCESS_INSTANCE_ID, TASK_TAG, TASK_ROLE_TAG, BUSINESS_OBJECT_ID, 
        CLASSDELEGATE_ADAPTER_TLI, CLASSDELEGATE_ADAPTER_TOI, FORM_KEY, 
        TASK_SERVICE_INVOKE_EXPRESSION, EXTEND_ATTRIBUTES, TASK_DEFINE_NAME, 
        ROOT_PROCESS_INSTANCE_ID, PROCESS_DEFINE_KEY
    }
    
    enum ProcessInformations {

        P_BUSINESS_OBJECT_ID, P_ROOT_PROCESS_INSTANCE_ID, P_PROCESS_DEFINE_KEY
    }
    
    public static final class XStreamSerializeHelper {
    	
    	private static final Pattern XMLPATTERN = Pattern.compile("<([A-Za-z0-9]+?)>([A-Za-z0-9.]*?)</");
        
        public static String serializeXml(String rootElement, Object target) {
            Assert.notNull(target);

            return serializeXml(rootElement, target, target.getClass());
        }

        public static String serializeXml(String rootElement, Object target, Class<?> targetClazz) {

            Assert.notNull(rootElement);
            Assert.notNull(target);
            Assert.notNull(targetClazz);

            XStream xstream = new XStream();
            xstream.alias(rootElement, targetClazz);

            return xstream.toXML(target);
        }
        
        @SuppressWarnings("unchecked")
		public static <T> T deserializeObject(String xml, String rootElement, Class<T> clazz) {
        	
        	Assert.notNull(xml);
        	Assert.notNull(clazz);
        	Assert.notNull(rootElement);
        	XStream xstream = new XStream();
        	xstream.alias(rootElement, clazz);
        	Object fromXml = xstream.fromXML(xml);
        	
        	return (T) fromXml;
        }
        
        /**
         * Check parameter given is XStream serialized or not.
         * @param xml check target
         * @return <code>True</code> only parameter is not <code>NULL</code> and is a regular XML fragment.
         */
        public static boolean isXStreamSerialized(Object xml) {
        	
        	if (xml == null) return false;
        	
        	if (!(xml instanceof String)) return false;
        	
        	try {
//        		XStreamSerializeHelper.XStreamSerializedXmlfactory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
//        		return true;
        		return XMLPATTERN.matcher(xml.toString()).find();
        	} catch (Exception e) {
        		return false;
        	}
        }
    }
    
    // ------------------------------------ Process related API ------------------------------------ //
    enum PROCESS_OPERATION_TYPE {

        SUSPEND, // Suspend operation
        RESUME, // Resume operation
        TERMINAL // Terminal operation
    }
    
    /**
     * return identity of implementation. Process engine name if ACTIVITI implementation
     * @return
     */
    String getName();
    
    /**
     * fix implementation's hash-code.
     * @return
     */
    int hashCode();
    
    /**
     * Start a process instance.
     * @param processDefinitionKey Process definition informations
     * @param processStarter Process starter informations
     * @param businessObjectId Business object ID <code>NOT NULL</code>
     * @param startParams Start parameters for calculate transition if need
     * @throws ProcessException Exception occurred during creation
     * @return engine task IDs
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.BO_ID, index=2)
    List<String> createProcessInstance(String processDefinitionKey, String processStarter, String businessObjectId, Map<String, Object> startParams) throws ProcessException;

    /**
     * Terminal process instance
     * @param engineProcessInstanceId engine process instance ID
     * @param operator operator
     * @param reason Reason for operation
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.PROCESS_INSTANCE_ID)
    void terminalProcessInstance(String engineProcessInstanceId, String operator, String reason) throws ProcessException;

    /**
     * Suspend process instance
     * @param engineProcessInstanceId engine process instance ID
     * @param operator operator
     * @param reason Reason for operation
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.PROCESS_INSTANCE_ID)
    void suspendProcessInstance(String engineProcessInstanceId, String operator, String reason) throws ProcessException;

    /**
     * Resume process instance
     * @param engineProcessInstanceId engine process instance ID
     * @param operator operator
     * @param reason Reason for operation
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.PROCESS_INSTANCE_ID)
    void resumeProcessInstance(String engineProcessInstanceId, String operator, String reason) throws ProcessException;
    
    /** 
     * Retrieve engine process instance ID by business object ID
     * @param businessObjectId business object ID
     * @param processDefinitionKey process definition key
     * @return BO id related engine process instance ID
     * @throws ProcessException Exception occurred during execution
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.BO_ID)
    String getEngineProcessInstanceIdByBOId(String businessObjectId, String processDefinitionKey) throws ProcessException;
    
    
    // ------------------------------------ Task related API ------------------------------------ //
    /**
     * Task form data key. 
     */
    String ENGINE_DRIVEN_TASK_FORM_DATA_KEY = WorkflowOperations.class.getName() + ".ENGINE_DRIVEN_TASK_FORM_DATA_KEY";
    /**
     * Task return data key.
     */
    String ENGINE_DRIVEN_TASK_RETURN_DATA_KEY = WorkflowOperations.class.getName() + ".ENGINE_DRIVEN_TASK_RETURN_DATA_KEY";


    /**
     * Complete task instance
     * @param engineTaskInstanceId engine task instance ID
     * @param operator operator
     * @param workflowParams Operation parameter for calculate transition if need
     * @return engine task IDs
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.TASK_INSTANCE_ID)
    List<String> completeTaskInstance(String engineTaskInstanceId, String operator, Map<String, Object> workflowParams) throws ProcessException;

    /**
     * Batch complete task instances
     * @param batchDTO DTO<EngineTaskInstanceID, WorkflowParams>
     * @param opeartor Operator
     * @return engine task IDs. Key is completion task instance ID
     */
    Map<String, List<String>> batchCompleteTaskIntances(Map<String, Map<String, Object>> batchDTO, String operator) throws ProcessException;

    /**
     * Obtain task instance related informations
     * @param engineTaskInstanceId engine task instance
     * @return related informations of task
     * 
     * @see TaskInformations
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.TASK_INSTANCE_ID)
    HashMap<String, String> getTaskInstanceInformations(String engineTaskInstanceId);
    
    /**
     * Obtain task instance related informations
     * @param engineTaskInstanceId engine task instance
     * @return related informations of task
     * 
     * @see TaskInformations
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.PROCESS_INSTANCE_ID)
    HashMap<String, String> getProcessInstanceInformations(String engineProcessInstanceId);

    /**
     * Get task name by given define ID.
     * @param processDefinitionKey  process definition key
     * @param taskDefineId task define ID
     * @return task name
     */
    String getTaskNameByDefineId(String processDefinitionKey, String taskDefineId);

    /**
     * Get process instance's variables
     * @param engineProcessInstanceId process instance ID(NOT NULL)
     * @return process instance's variables
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.PROCESS_INSTANCE_ID)
    Set<String> getProcessInstanceVariableNames(String engineProcessInstanceId);
    
    /**
     * Get process instance's variables
     * @param engineProcessInstanceId process instance ID(NOT NULL)
     * @return process instance's variables
     */
    Set<String> getLastedVersionProcessDefinitionVariableNames(String processDefinitionKey);

    /**
     * Obtain task executer role
     * @param engineTaskInstanceId engine task instance ID
     * @return task executer role
     * @throws ProcessException Exception occurred during creation
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.TASK_INSTANCE_ID)
    String obtainTaskRole(String engineTaskInstanceId) throws ProcessException;

    /**
     * Reassign task executer
     * @param engineProcessInstanceId engine process instance ID
     * @param engineTaskInstanceId engine task instance ID
     * @param oldExecuter original executer
     * @param newExecuter new executer
     */
    @ScaleoutKeySource(value = RETRIEVE_TYPE.TASK_INSTANCE_ID, index = 1)
    void reassignTaskExecuter(String engineProcessInstanceId, String engineTaskInstanceId, String oldExecuter, String newExecuter) throws ProcessException;

}
