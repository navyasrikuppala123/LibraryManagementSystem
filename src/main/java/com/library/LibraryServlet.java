
package com.library;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet("/library")
public class LibraryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("username") == null) {
            response.sendRedirect(request.getContextPath() + "/index.html");
            return;
        }

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String role = (String) session.getAttribute("role");

        out.println("<!DOCTYPE html><html><head>");
        out.println("<title>Library Dashboard</title>");
        out.println("<link rel='stylesheet' href='style.css'>");
        out.println("</head><body>");

        out.println("<h1>Library Management System</h1>");
        out.println("<h3>Welcome, " +
                escape((String) session.getAttribute("username")) +
                " (" + escape(role) + ")</h3>");
        out.println("<p><a href='logout'>Logout</a></p>");

        if ("librarian".equalsIgnoreCase(role)) {
            out.println("<h2>Add Book</h2>");
            out.println("<form method='post' action='library'>");
            out.println("<input type='hidden' name='action' value='book'>");
            out.println("<input name='title' placeholder='Book title' required>");
            out.println("<input name='author' placeholder='Author' required>");
            out.println("<input type='number' name='quantity' min='1' placeholder='Quantity' required>");
            out.println("<button type='submit'>Add Book</button></form>");

            out.println("<h2>Register Student</h2>");
            out.println("<form method='post' action='library'>");
            out.println("<input type='hidden' name='action' value='student'>");
            out.println("<input name='name' placeholder='Student name' required>");
            out.println("<input type='email' name='email' placeholder='Email' required>");
            out.println("<button type='submit'>Register Student</button></form>");
        }

        out.println("<h2>Available Books</h2>");
        out.println("<table><tr><th>ID</th><th>Title</th><th>Author</th><th>Quantity</th></tr>");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id,title,author,quantity FROM books");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.println("<tr><td>" + rs.getInt("id") +
                        "</td><td>" + escape(rs.getString("title")) +
                        "</td><td>" + escape(rs.getString("author")) +
                        "</td><td>" + rs.getInt("quantity") + "</td></tr>");
            }
        } catch (Exception e) {
            throw new ServletException("Could not load books", e);
        }

        out.println("</table>");

        out.println("<h2>Issue a Book</h2>");
        out.println("<form method='post' action='library'>");
        out.println("<input type='hidden' name='action' value='issue'>");
        out.println("<input type='number' name='studentId' min='1' placeholder='Student ID' required>");
        out.println("<input type='number' name='bookId' min='1' placeholder='Book ID' required>");
        out.println("<button type='submit'>Issue Book</button></form>");

        out.println("<h2>Issued Books</h2>");
        out.println("<table><tr><th>Issue ID</th><th>Student</th><th>Book</th></tr>");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT i.id,s.name,b.title FROM issued_books i " +
                             "JOIN students s ON i.student_id=s.id " +
                             "JOIN books b ON i.book_id=b.id");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.println("<tr><td>" + rs.getInt(1) +
                        "</td><td>" + escape(rs.getString(2)) +
                        "</td><td>" + escape(rs.getString(3)) + "</td></tr>");
            }
        } catch (Exception e) {
            throw new ServletException("Could not load issued books", e);
        }

        out.println("</table></body></html>");
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("username") == null) {
            response.sendRedirect(request.getContextPath() + "/index.html");
            return;
        }

        String action = request.getParameter("action");
        String role = (String) session.getAttribute("role");

        if ("book".equals(action) || "student".equals(action)) {
            if (!"librarian".equalsIgnoreCase(role)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Only librarians can perform this action.");
                return;
            }
        }

        try (Connection con = DBConnection.getConnection()) {

            if ("book".equals(action)) {
                String sql = "INSERT INTO books(title,author,quantity) VALUES(?,?,?)";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, request.getParameter("title"));
                    ps.setString(2, request.getParameter("author"));
                    ps.setInt(3, Integer.parseInt(request.getParameter("quantity")));
                    ps.executeUpdate();
                }

            } else if ("student".equals(action)) {
                String sql = "INSERT INTO students(name,email) VALUES(?,?)";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, request.getParameter("name"));
                    ps.setString(2, request.getParameter("email"));
                    ps.executeUpdate();
                }

            } else if ("issue".equals(action)) {
                int studentId = Integer.parseInt(request.getParameter("studentId"));
                int bookId = Integer.parseInt(request.getParameter("bookId"));

                con.setAutoCommit(false);

                try {
                    int quantity;

                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT quantity FROM books WHERE id=? FOR UPDATE")) {
                        ps.setInt(1, bookId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next() || rs.getInt("quantity") <= 0) {
                                throw new IllegalArgumentException(
                                        "Book does not exist or is unavailable.");
                            }
                            quantity = rs.getInt("quantity");
                        }
                    }

                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT id FROM students WHERE id=?")) {
                        ps.setInt(1, studentId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new IllegalArgumentException(
                                        "Student ID does not exist.");
                            }
                        }
                    }

                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO issued_books(student_id,book_id) VALUES(?,?)")) {
                        ps.setInt(1, studentId);
                        ps.setInt(2, bookId);
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE books SET quantity=quantity-1 WHERE id=?")) {
                        ps.setInt(1, bookId);
                        ps.executeUpdate();
                    }

                    con.commit();
                } catch (Exception e) {
                    con.rollback();
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }

            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid action.");
                return;
            }

            response.sendRedirect(request.getContextPath() + "/library");

        } catch (IllegalArgumentException e) {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h3>" + escape(e.getMessage()) +
                    "</h3><a href='library'>Back to Library</a>");
        } catch (Exception e) {
            throw new ServletException("Library operation failed", e);
        }
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
