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
package org.rill.bpm.common.mail.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rill.bpm.common.mail.TemplateMailSender;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


public class TemplateMailSenderSupport {

	public static final String DEFAULT_ENCODE = "UTF-8";
	protected final Log logger = LogFactory.getLog(getClass().getName());
	
	private JavaMailSender mailSender;
	private TaskExecutor taskExecutor;
	private String encode = DEFAULT_ENCODE;

	/**
	 * @return the encode
	 */
	public String getEncode() {
		return encode;
	}

	/**
	 * @param encode the encode to set
	 */
	public void setEncode(String encode) {
		this.encode = encode;
		Assert.hasLength(this.encode, "Encode is mismatch");
	}

	/**
	 * @return the mailSender
	 */
	public JavaMailSender getMailSender() {
		return mailSender;
	}

	/**
	 * @param mailSender the mailSender to set
	 */
	public void setMailSender(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	/**
	 * @return the taskExecutor
	 */
	public TaskExecutor getTaskExecutor() {
		return taskExecutor;
	}

	/**
	 * @param taskExecutor the taskExecutor to set
	 */
	public void setTaskExecutor(TaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	protected final boolean isMultipart(Map<String, Object> model) {
		
		if (model.containsKey(TemplateMailSender.ATTACHMENT_KEY)) {
			return true;
		}
		
		return false;
	}
	
	@SuppressWarnings("unchecked")
	protected MimeMessageHelper processMultipart(Map<String, Object> model, MimeMessageHelper messageHelper) throws MessagingException {
		
		// Handle multipart
		if (isMultipart(model)) {
			List<AttachmentWarper> list = new ArrayList<AttachmentWarper>();
			Object attachments = model.get(TemplateMailSender.ATTACHMENT_KEY);
			if (attachments.getClass().isArray()) {
				AttachmentWarper[] awArrays = ((AttachmentWarper[]) attachments);
                                list.addAll(Arrays.asList(awArrays));
			} else if (attachments instanceof Collection) {
				Collection<AttachmentWarper> c = (Collection<AttachmentWarper>) attachments;
				list.addAll(c);
			} else {
				list.add(((AttachmentWarper) attachments));
			}
			
			for (AttachmentWarper aw : list) {
				if (aw.getContentType() == null)
					messageHelper.addAttachment(aw.getAttachmentFileName(), aw.getInputStreamSource());
				else
					messageHelper.addAttachment(aw.getAttachmentFileName(), aw.getInputStreamSource(), aw.getContentType());
			}
		}
		
		// Handle multipart
		if (isMultipart(model)) {
			List<AttachmentWarper> list = new ArrayList<AttachmentWarper>();
			Object inline = model.get(TemplateMailSender.INLINE_KEY);
			if (inline.getClass().isArray()) {
				AttachmentWarper[] awArrays = ((AttachmentWarper[]) inline);
                                list.addAll(Arrays.asList(awArrays));
			} else if (inline instanceof Collection) {
				Collection<AttachmentWarper> c = (Collection<AttachmentWarper>) inline;
				list.addAll(c);
			} else {
				list.add(((AttachmentWarper) inline));
			}
			
			for (AttachmentWarper aw : list) {
				String cid = StringUtils.replace(aw.getAttachmentFileName(), ".", "_");
				if (aw.getContentType() == null)
					messageHelper.addInline(cid, aw.getInputStreamSource(), messageHelper.getFileTypeMap().getContentType(aw.getAttachmentFileName()));
				else
					messageHelper.addInline(cid, aw.getInputStreamSource(), aw.getContentType());
			}
		}
		
		return messageHelper;
	}
	
}
