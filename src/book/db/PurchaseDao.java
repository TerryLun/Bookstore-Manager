package book.db;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import book.ApplicationException;
import book.data.Purchase;
import book.io.PurchaseReader;

/**
 * @author Terry Tianwei Lun
 *
 */
public class PurchaseDao extends Dao {

	public static final String TABLE_NAME = DbConstants.PURCHASE_TABLE_NAME;

	private static final String PURCHASE_DATA_FILENAME = "purchases.csv";
	private static final Logger LOG = LogManager.getLogger();
	private static final PurchaseDao theInstance = new PurchaseDao();
	private static Database database;

	private PurchaseDao() {
		super(TABLE_NAME);

		database = Database.getTheInstance();
	}

	public static PurchaseDao getTheInstance() {
		return theInstance;
	}

	/**
	 * @param purchaseDataFile
	 * @throws ApplicationException Application Exception
	 */
	public void init() throws ApplicationException {
		File purchaseDataFile = new File(PURCHASE_DATA_FILENAME);
		try {
			if (!Database.tableExists(PurchaseDao.TABLE_NAME) || Database.dbTableDropRequested()) {
				if (Database.tableExists(PurchaseDao.TABLE_NAME) && Database.dbTableDropRequested()) {
					drop();
				}

				create();

				LOG.debug("Inserting the purchases");

				if (!purchaseDataFile.exists()) {
					throw new ApplicationException(String.format("Required '%s' is missing.", PURCHASE_DATA_FILENAME));
				}

				PurchaseReader.read(purchaseDataFile, this);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new ApplicationException(e);
		}
	}

	@Override
	public void create() throws SQLException {
		LOG.debug("Creating database table " + TABLE_NAME);

		String sqlString = String.format("CREATE TABLE %s(" //
				+ "%s BIGINT, " // ID
				+ "%s BIGINT, " // CUSTOMER_ID
				+ "%s BIGINT, " // BOOK_ID
				+ "%s FLOAT, " // PRICE
				+ "PRIMARY KEY (%s), " // ID
				+ "FOREIGN KEY (%s) " // CUSTOMER_ID
				+ "REFERENCES %s (%s), " //
				+ "FOREIGN KEY (%s) " // BOOK_ID
				+ "REFERENCES %s (%s)) ", //
				TABLE_NAME, //
				Column.ID.name, //
				Column.CUSTOMER_ID.name, //
				Column.BOOK_ID.name, //
				Column.PRICE.name, //
				Column.ID.name, //
				Column.CUSTOMER_ID.name, //
				DbConstants.CUSTOMER_TABLE_NAME, //
				Column.ID.name, //
				Column.BOOK_ID.name, //
				DbConstants.BOOK_TABLE_NAME, //
				Column.ID.name);

	
		super.create(sqlString);
	}

	public void add(Purchase purchase) throws SQLException {
		String sqlString = String.format("INSERT INTO %s values(?, ?, ?, ?)", TABLE_NAME);
		boolean result = execute(sqlString, //
				purchase.getId(), //
				purchase.getCustomerId(), //
				purchase.getBookId(), //
				purchase.getPrice());
		LOG.debug(String.format("Adding %s was %s", purchase, result ? "successful" : "unsuccessful"));
	}

	/**
	 * Update the purchase.
	 *
	 * @param purchase purchase
	 * @throws SQLException SQL Exception
	 */
	public void update(Purchase purchase) throws SQLException {
		String sqlString = String.format("UPDATE %s SET %s=?, %s=?, %s=? WHERE %s=?", TABLE_NAME, //
				Column.CUSTOMER_ID.name, //
				Column.BOOK_ID.name, //
				Column.PRICE.name, //
				Column.ID.name);
		LOG.debug("Update statment: " + sqlString);

		boolean result = execute(sqlString, //
				purchase.getCustomerId(), //
				purchase.getBookId(), //
				purchase.getPrice(), //
				purchase.getId());
		LOG.debug(String.format("Updating %s was %s", purchase, result ? "successful" : "unsuccessful"));
	}

	/**
	 * Delete the book from the database.
	 *
	 * @param purchase purchase
	 * @throws SQLException SQL Exception
	 */
	public void delete(Purchase purchase) throws SQLException {
		Connection connection;
		Statement statement = null;
		try {
			connection = database.getConnection();
			statement = connection.createStatement();

			String sqlString = String.format("DELETE FROM %s WHERE %s='%s'", TABLE_NAME, Column.ID.name, purchase.getId());
			LOG.debug(sqlString);
			int rowcount = statement.executeUpdate(sqlString);
			LOG.debug(String.format("Deleted %d rows", rowcount));
		} finally {
			close(statement);
		}
	}

	/**
	 * Retrieve all the purchase IDs from the database
	 *
	 * @return the list of purchase IDs
	 * @throws SQLException SQL Exception
	 */
	public List<Long> getPurchaseIds() throws SQLException {
		List<Long> ids = new ArrayList<>();

		String selectString = String.format("SELECT %s FROM %s", Column.ID.name, TABLE_NAME);
		LOG.debug(selectString);

		Statement statement = null;
		ResultSet resultSet = null;
		try {
			Connection connection = database.getConnection();
			statement = connection.createStatement();
			resultSet = statement.executeQuery(selectString);

			while (resultSet.next()) {
				ids.add(resultSet.getLong(Column.ID.name));
			}

		} finally {
			close(statement);
		}

		LOG.debug(String.format("Loaded %d purchase IDs from the database", ids.size()));

		return ids;
	}

	/**
	 * @param purchaseId purchase Id
	 * @return Purchase
	 * @throws Exception Exception
	 */
	public Purchase getPurchase(Long purchaseId) throws Exception {
		String sqlString = String.format("SELECT * FROM %s WHERE %s = %d", TABLE_NAME, Column.ID.name, purchaseId);
//		LOG.debug(sqlString);

		Statement statement = null;
		ResultSet resultSet = null;
		try {
			Connection connection = database.getConnection();
			statement = connection.createStatement();
			resultSet = statement.executeQuery(sqlString);

			int count = 0;
			while (resultSet.next()) {
				count++;
				if (count > 1) {
					throw new ApplicationException(String.format("Expected one result, got %d", count));
				}

				Purchase purchase = new Purchase.Builder(resultSet.getInt(Column.ID.name), //
						resultSet.getInt(Column.CUSTOMER_ID.name), //
						resultSet.getInt(Column.BOOK_ID.name), //
						resultSet.getFloat(Column.PRICE.name)).build();

				return purchase;
			}
		} finally {
			close(statement);
		}

		return null;
	}

	/**
	 * @return purchase count
	 * @throws Exception Exception
	 */
	public int countAllPurchases() throws Exception {
		Statement statement = null;
		int count = 0;
		try {
			Connection connection = database.getConnection();
			statement = connection.createStatement();
			// Execute a statement
			String sqlString = String.format("SELECT COUNT(*) AS total FROM %s", TABLE_NAME);
			ResultSet resultSet = statement.executeQuery(sqlString);
			if (resultSet.next()) {
				count = resultSet.getInt("total");
			}
		} finally {
			close(statement);
		}
		return count;
	}

	public enum Column {
		ID("id", 16), //
		CUSTOMER_ID("customerId", 16), //
		BOOK_ID("bookId", 16), //
		PRICE("price", 16); //

		String name;
		int length;

		private Column(String name, int length) {
			this.name = name;
			this.length = length;
		}
	}
}