/** 
 * Copyright 2018-2028 Akaxin Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package com.akaxin.site.message.group.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akaxin.common.channel.ChannelSession;
import com.akaxin.common.command.Command;
import com.akaxin.common.command.RedisCommand;
import com.akaxin.common.constant.CommandConst;
import com.akaxin.proto.client.ImStcMessageProto;
import com.akaxin.proto.core.CoreProto;
import com.akaxin.proto.core.CoreProto.MsgType;
import com.akaxin.proto.site.ImCtsMessageProto;
import com.akaxin.site.storage.api.IMessageDao;
import com.akaxin.site.storage.bean.GroupMessageBean;
import com.akaxin.site.storage.service.MessageDaoService;

import io.netty.channel.Channel;

public class GroupMessageImageHandler extends AbstractGroupHandler<Command> {
	private static final Logger logger = LoggerFactory.getLogger(GroupMessageImageHandler.class);
	private IMessageDao messageDao = new MessageDaoService();

	public boolean handle(Command command) {
		ChannelSession channelSession = command.getChannelSession();

		try {
			ImCtsMessageProto.ImCtsMessageRequest request = ImCtsMessageProto.ImCtsMessageRequest
					.parseFrom(command.getParams());

			int type = request.getType().getNumber();
			if (CoreProto.MsgType.GROUP_IMAGE_VALUE == type) {
				String siteUserId = command.getSiteUserId();
				String deviceId = command.getDeviceId();
				String gmsgId = request.getGroupImage().getMsgId();
				String groupId = request.getGroupImage().getSiteGroupId();
				String groupImageId = request.getGroupImage().getImageId();

				GroupMessageBean gmsgBean = new GroupMessageBean();
				gmsgBean.setMsgId(gmsgId);
				gmsgBean.setSendUserId(siteUserId);
				gmsgBean.setSendDeviceId(deviceId);
				gmsgBean.setSiteGroupId(groupId);
				gmsgBean.setContent(groupImageId);
				gmsgBean.setMsgType(type);
				gmsgBean.setMsgTime(System.currentTimeMillis());

				logger.info("Group Image message bean={}", gmsgBean.toString());

				messageDao.saveGroupMessage(gmsgBean);
				msgResponse(channelSession.getChannel(), command, siteUserId, groupId, gmsgId);

			}
			return true;
		} catch (Exception e) {
			logger.error("save group image message error.", e);
		}

		return false;
	}

	private void msgResponse(Channel channel, Command command, String from, String to, String msgId) {
		CoreProto.MsgStatus status = CoreProto.MsgStatus.newBuilder().setMsgId(msgId).setMsgStatus(1).build();

		ImStcMessageProto.MsgWithPointer statusMsg = ImStcMessageProto.MsgWithPointer.newBuilder()
				.setType(MsgType.MSG_STATUS).setStatus(status).build();

		ImStcMessageProto.ImStcMessageRequest request = ImStcMessageProto.ImStcMessageRequest.newBuilder()
				.addList(0, statusMsg).build();

		CoreProto.TransportPackageData data = CoreProto.TransportPackageData.newBuilder()
				.setData(request.toByteString()).build();

		channel.writeAndFlush(new RedisCommand().add(CommandConst.PROTOCOL_VERSION).add(CommandConst.IM_MSG_TOCLIENT)
				.add(data.toByteArray()));

	}

}
