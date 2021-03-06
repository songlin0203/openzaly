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
package com.akaxin.site.storage.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akaxin.common.logs.LogUtils;
import com.akaxin.site.storage.bean.GroupMessageBean;
import com.akaxin.site.storage.sqlite.manager.SQLiteJDBCManager;
import com.akaxin.site.storage.sqlite.sql.SQLConst;

/**
 * 群消息数据库相关操作
 * 
 * @author Sam{@link an.guoyue254@gmail.com}
 * @since 2018-01-25 16:15:32
 */
public class SQLiteGroupMessageDao {
	private static final Logger logger = LoggerFactory.getLogger(SQLiteGroupMessageDao.class);
	private final String GROUP_MESSAGE_TABLE = SQLConst.SITE_GROUP_MESSAGE;
	private final String GROUP_POINTER_TABLE = SQLConst.SITE_GROUP_MESSAGE_POINTER;
	private static SQLiteGroupMessageDao instance = new SQLiteGroupMessageDao();

	public static SQLiteGroupMessageDao getInstance() {
		return instance;
	}

	public boolean saveGroupMessage(GroupMessageBean gmsgBean) throws SQLException {
		long startTime = System.currentTimeMillis();
		String insertSql = "INSERT INTO " + GROUP_MESSAGE_TABLE
				+ "(site_group_id,msg_id,send_user_id,send_device_id,msg_type,content,msg_time) VALUES(?,?,?,?,?,?,?);";
		PreparedStatement preStatement = SQLiteJDBCManager.getConnection().prepareStatement(insertSql);
		preStatement.setString(1, gmsgBean.getSiteGroupId());
		preStatement.setString(2, gmsgBean.getMsgId());
		preStatement.setString(3, gmsgBean.getSendUserId());
		preStatement.setString(4, gmsgBean.getSendDeviceId());
		preStatement.setLong(5, gmsgBean.getMsgType());
		preStatement.setString(6, gmsgBean.getContent());
		preStatement.setLong(7, gmsgBean.getMsgTime());

		int insertResult = preStatement.executeUpdate();

		long endTime = System.currentTimeMillis();
		LogUtils.printDBLog(logger, endTime - startTime, insertResult, insertSql);

		return insertResult == 1;

	}

	/**
	 * 查询的结果，排除发送者的deviceId
	 * 
	 * @param groupId
	 * @param userId
	 * @param deviceId
	 * @param start
	 * @return
	 * @throws SQLException
	 */
	public List<GroupMessageBean> queryGroupMessage(String groupId, String userId, String deviceId, long start)
			throws SQLException {
		long startTime = System.currentTimeMillis();
		List<GroupMessageBean> gmsgList = new ArrayList<GroupMessageBean>();
		String querySql = "SELECT a.id,a.site_group_id,a.msg_id,a.send_user_id,a.send_device_id,a.msg_type,a.content,a.msg_time FROM "
				+ GROUP_MESSAGE_TABLE
				+ " AS a LEFT JOIN site_group_profile AS b WHERE a.site_group_id=b.site_group_id AND a.site_group_id=? AND a.id>? AND b.group_status=1 AND a.send_device_id IS NOT ?;";

		start = queryGroupPointer(groupId, userId, deviceId, start);

		if (start == 0) {
			start = queryMaxGroupPointerWithUser(groupId, userId);
		}

		PreparedStatement statement = SQLiteJDBCManager.getConnection().prepareStatement(querySql);
		statement.setString(1, groupId);
		statement.setLong(2, start);
		statement.setString(3, deviceId);

		ResultSet rs = statement.executeQuery();

		while (rs.next()) {
			GroupMessageBean gmsgBean = new GroupMessageBean();
			gmsgBean.setId(rs.getInt(1));
			gmsgBean.setSiteGroupId(rs.getString(2));
			gmsgBean.setMsgId(rs.getString(3));
			gmsgBean.setSendUserId(rs.getString(4));
			gmsgBean.setSendDeviceId(rs.getString(5));
			gmsgBean.setMsgType(rs.getInt(6));
			gmsgBean.setContent(rs.getString(7));
			gmsgBean.setMsgTime(rs.getLong(8));
			gmsgList.add(gmsgBean);
		}

		long endTime = System.currentTimeMillis();
		LogUtils.printDBLog(logger, endTime - startTime, "gListSize=" + String.valueOf(gmsgList.size()),
				querySql + ",start=" + start + ",userid=" + userId + ",deviceId=" + deviceId + ",groupId=" + groupId);

		return gmsgList;
	}

	public boolean updateGroupMessagePointer(String groupId, String siteUserId, String deviceId, long finishPointer)
			throws SQLException {
		int result = updateGroupPointer(groupId, siteUserId, deviceId, finishPointer);

		logger.info("Update Group Pointer siteUserId={} groupId={} gPointers={} result={}", siteUserId, groupId,
				finishPointer, result);

		if (result >= 1) {
			return true;
		}
		return saveGroupPointer(groupId, siteUserId, deviceId, finishPointer);
	}

	public boolean saveGroupPointer(String groupId, String userId, String deviceId, long finish) throws SQLException {
		int result = 0;
		String updateSql = "INSERT INTO " + GROUP_POINTER_TABLE
				+ "(site_group_id,site_user_id,device_id,pointer) VALUES(?,?,?,?);";
		long startTime = System.currentTimeMillis();
		PreparedStatement pStatement = SQLiteJDBCManager.getConnection().prepareStatement(updateSql);
		pStatement.setString(1, groupId);
		pStatement.setString(2, userId);
		pStatement.setString(3, deviceId);
		pStatement.setLong(4, finish);

		result = pStatement.executeUpdate();

		long endTime = System.currentTimeMillis();
		LogUtils.printDBLog(logger, endTime - startTime, result,
				updateSql + "," + finish + "," + userId + "," + groupId + "," + deviceId);
		return result == 1;
	}

	private int updateGroupPointer(String groupId, String userId, String deviceId, long finish) throws SQLException {
		int result = 0;
		String updateSql = "UPDATE " + GROUP_POINTER_TABLE
				+ " SET pointer=? WHERE site_user_id=? AND site_group_id=? AND device_id=?;";
		long startTime = System.currentTimeMillis();

		PreparedStatement pStatement = SQLiteJDBCManager.getConnection().prepareStatement(updateSql);
		pStatement.setLong(1, finish);
		pStatement.setString(2, userId);
		pStatement.setString(3, groupId);
		pStatement.setString(4, deviceId);

		result = pStatement.executeUpdate();

		long endTime = System.currentTimeMillis();
		LogUtils.printDBLog(logger, endTime - startTime, result,
				updateSql + "," + finish + "," + userId + "," + groupId + "," + deviceId);

		return result;
	}

	private long queryGroupPointer(String groupId, String userId, String deviceId, long start) {
		long startTime = System.currentTimeMillis();
		long pointer = 0;
		String sql = "SELECT pointer FROM " + GROUP_POINTER_TABLE
				+ " WHERE site_user_id=? AND site_group_id=? AND device_id=?;";

		try {
			PreparedStatement pStatement = SQLiteJDBCManager.getConnection().prepareStatement(sql);
			pStatement.setString(1, userId);
			pStatement.setString(2, groupId);
			pStatement.setString(3, deviceId);

			ResultSet prs = pStatement.executeQuery();
			if (prs.next()) {
				pointer = prs.getLong(1);
			}
		} catch (SQLException e) {
			logger.error("query group message pointer error.", e);
		}

		long endTime = System.currentTimeMillis();
		LogUtils.printDBLog(logger, endTime - startTime, pointer, sql + "," + userId + "," + groupId + "," + deviceId);

		return pointer > start ? pointer : start;
	}

	private long queryMaxGroupPointerWithUser(String groupId, String siteUserId) {
		long startTime = System.currentTimeMillis();
		long pointer = 0;
		String sql = "SELECT max(pointer) FROM " + GROUP_POINTER_TABLE + " WHERE site_user_id=? AND site_group_id=?;";
		try {
			PreparedStatement pStatement = SQLiteJDBCManager.getConnection().prepareStatement(sql);
			pStatement.setString(1, siteUserId);
			pStatement.setString(2, groupId);

			ResultSet prs = pStatement.executeQuery();
			if (prs.next()) {
				pointer = prs.getLong(1);
			}
		} catch (SQLException e) {
			logger.error("query group message pointer error.", e);
		}

		long endTime = System.currentTimeMillis();
		LogUtils.printDBLog(logger, endTime - startTime, pointer, sql + "," + siteUserId + "," + groupId);

		return pointer;
	}

	public long queryMaxGroupPointer(String groupId) {
		long startTime = System.currentTimeMillis();
		long pointer = 0;
		String sql = "SELECT MAX(id) FROM " + GROUP_MESSAGE_TABLE + " WHERE site_group_id=?;";
		try {
			PreparedStatement pStatement = SQLiteJDBCManager.getConnection().prepareStatement(sql);
			pStatement.setString(1, groupId);

			ResultSet prs = pStatement.executeQuery();
			if (prs.next()) {
				pointer = prs.getLong(1);
			}
		} catch (SQLException e) {
			logger.error("query max group message pointer error.", e);
		}

		long endTime = System.currentTimeMillis();
		LogUtils.printDBLog(logger, endTime - startTime, pointer, sql + groupId);

		return pointer;
	}

}
