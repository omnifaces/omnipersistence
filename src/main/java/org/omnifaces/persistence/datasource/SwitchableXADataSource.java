package org.omnifaces.persistence.datasource;

import java.sql.SQLException;

import javax.sql.XAConnection;
import javax.sql.XADataSource;

public class SwitchableXADataSource extends SwitchableCommonDataSource implements XADataSource {

	@Override
	public XADataSource getWrapped() {
		return (XADataSource) super.getWrapped();
	}

	// ------------------------- XADataSource-----------------------------------

	@Override
	public XAConnection getXAConnection() throws SQLException {
		return getWrapped().getXAConnection();
	}

	@Override
	public XAConnection getXAConnection(String user, String password) throws SQLException {
		return getWrapped().getXAConnection();
	}

}