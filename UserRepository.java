package com.immersivelabs.fitness.repository;

import com.immersivelabs.fitness.model.User;
import com.immersivelabs.fitness.utils.PasswordUtils;
import com.immersivelabs.fitness.utils.StringUtils;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public class UserRepository extends BaseRepository {

    private static final String SELECT_USER_BY_USERNAME_SQL = "SELECT * FROM users WHERE username = ?";

    private static final String SELECT_USER_BY_USER_AND_PASSWORD_SQL = "SELECT * FROM users WHERE username = ? AND password = ?";

    private static final String DELETE_RESET_BY_USER_ID_SQL = "DELETE FROM reset WHERE user_id = ?";

    private static final String UPDATE_USER_PASSWORD_SQL = "UPDATE users SET password = ? WHERE username = ?";

    private static final String RESET_PASSWORD_SQL = "UPDATE users SET password = ? WHERE id = ?";

    private static final String INSERT_USER_SQL = "INSERT INTO users (username, first_name, last_name, password, admin, email, location) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String DELETE_USER_SQL = "DELETE FROM users WHERE username = ?";

    private static final String INSERT_RESET_SQL = "INSERT INTO reset (user_id, token) VALUES (?,?)";

    private static final String SELECT_RESET_SQL = "SELECT * FROM reset WHERE user_id = ? AND token = ? AND created_at > ?";

    public Optional<User> getUser(String userName) {
        Connection connection = DatabaseConnection.connect();
        User user = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = connection.prepareStatement(SELECT_USER_BY_USERNAME_SQL);
            stmt.setString(1, userName);

            rs = stmt.executeQuery();
            if (rs.next()) {
                user = new User(rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        null,
                        rs.getInt("admin"),
                        rs.getString("email"),
                        rs.getString("location"));
            } else {
                return Optional.empty();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            close(rs, stmt, connection);
        }
        return Optional.of(user);
    }

    public Optional<User> login(String userName, String password) {
        Connection connection = DatabaseConnection.connect();
        User user = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String hashedPassword = PasswordUtils.hashPassword(password);

            Statement statement = connection.createStatement();
            rs = statement.executeQuery(String.format("SELECT * FROM users WHERE username = '%s' AND password = '%s'",
                    userName, hashedPassword));

            if (rs.next()) {
                user = new User(rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        null,
                        rs.getInt("admin"),
                        rs.getString("email"),
                        rs.getString("location"));

                this.deleteResetTokens(user.getId());
            } else {
                return Optional.empty();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            close(rs, stmt, connection);
        }
        return Optional.of(user);
    }

    public Boolean changePassword(String userName, String oldPassword, String newPassword) {
        return this.login(userName, oldPassword).map(user -> {
            Connection connection = DatabaseConnection.connect();
            PreparedStatement stmt = null;
            try {
                String hashedPassword = PasswordUtils.hashPassword(newPassword);

                stmt = connection.prepareStatement(UPDATE_USER_PASSWORD_SQL);
                stmt.setString(1, hashedPassword);
                stmt.setString(2, userName);

                stmt.executeUpdate();

                if (!connection.getAutoCommit()) {
                    connection.commit();
                }

                return true;
            } catch (SQLException e) {
                System.out.println(e.getMessage());
                return false;
            } finally {
                close(stmt, connection);
            }
        }).orElse(false);
    }

    public Boolean resetPassword(Integer userId, String newPassword) {
        Connection connection = DatabaseConnection.connect();
        PreparedStatement stmt = null;
        try {
            String hashedPassword = PasswordUtils.hashPassword(newPassword);

            stmt = connection.prepareStatement(RESET_PASSWORD_SQL);
            stmt.setString(1, hashedPassword);
            stmt.setInt(2, userId);
            stmt.executeUpdate();

            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        } finally {
            close(stmt, connection);
        }
        return true;
    }

    private void insertUser(String userName, String firstName, String lastName, String password, String email,
            String location) {
        final Connection connection = DatabaseConnection.connect();
        PreparedStatement stmt = null;

        try {
            final String hashedPassword = PasswordUtils.hashPassword(password);
            stmt = connection.prepareStatement(INSERT_USER_SQL);
            stmt.setString(1, userName);
            stmt.setString(2, firstName);
            stmt.setString(3, lastName);
            stmt.setString(4, hashedPassword);
            stmt.setInt(5, 0);
            stmt.setString(6, email);
            stmt.setString(7, location);

            stmt.executeUpdate();

            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            close(stmt, connection);
        }
    }

    public Object register(String userName, String firstName, String lastName, String password, String email,
            String location) {
        Optional<User> optionalUser = this.getUser(userName);
        if (optionalUser.isPresent()) {
            return Boolean.FALSE;
        }

        this.insertUser(userName, firstName, lastName, password, email, location);
        return this.login(userName, password).get();
    }

    public Boolean userExists(String userName) {
        return this.getUser(userName).isPresent();
    }

    public Boolean delete(String userName) {
        final Connection connection = DatabaseConnection.connect();
        PreparedStatement stmt = null;

        try {
            stmt = connection.prepareStatement(DELETE_USER_SQL);
            stmt.setString(1, userName);
            stmt.executeUpdate();

            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        } finally {
            close(stmt, connection);
        }

        if (!this.userExists(userName)) {
            return true;
        }
        return false;
    }

    public void deleteResetTokens(Integer userId) {
        final Connection connection = DatabaseConnection.connect();
        PreparedStatement ps = null;

        try {
            ps = connection.prepareStatement(DELETE_RESET_BY_USER_ID_SQL);
            ps.setInt(1, userId);
            ps.executeUpdate();

            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            close(ps, connection);
        }
    }

    public String generateResetToken(String userName) {
        return this.getUser(userName).map(user -> {
            this.deleteResetTokens(user.getId());
            String token = StringUtils.randomString(16);

            Connection connection = DatabaseConnection.connect();
            PreparedStatement ps = null;
            try {
                ps = connection.prepareStatement(INSERT_RESET_SQL);
                ps.setInt(1, user.getId());
                ps.setString(2, token);
                ps.executeUpdate();

                if (!connection.getAutoCommit()) {
                    connection.commit();
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            } finally {
                close(ps, connection);
            }
            return token;
        }).orElse(null);
    }

    public Boolean validateResetToken(Integer userId, String token) {
        Connection connection = DatabaseConnection.connect();
        PreparedStatement ps = null;
        ResultSet rs = null;
        Boolean hasResults = false;
        try {
            final Date nowPlusThirtyMins = (Date) Date.from(Instant.now().plusSeconds(1800));
            ps = connection.prepareStatement(SELECT_RESET_SQL);
            ps.setInt(1, userId);
            ps.setString(2, token);
            ps.setDate(3, nowPlusThirtyMins);
            rs = ps.executeQuery();
            if (rs.next()) {
                hasResults = true;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            this.close(rs, ps, connection);
        }
        return hasResults;
    }
}
