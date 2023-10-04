package com.immersivelabs.fitness.servlet;

import com.immersivelabs.fitness.model.User;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;

@WebServlet(urlPatterns = {"/login"})
public class LoginServlet extends HttpServlet {

    private final UserService userService = new UserService();

    private final String LOGIN_PAGE = "login.jsp";

    private final String LANDING_ACTION = "/landing";

    public void init(ServletConfig config) {
        System.out.println("LoginServlet initialized...");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doForward(LOGIN_PAGE, req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final String username = req.getParameter("username");
        final String password = req.getParameter("password");

        Optional<User> optionalUser = userService.getUserRepository().getUser(username);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            req.getSession().setAttribute(SessionKeys.USER_ID_KEY, user.getId());
            req.getSession().setAttribute(SessionKeys.USER_NAME_KEY, user.getUsername());

            resp.sendRedirect(req.getContextPath() + LANDING_ACTION);
            return;
        }

        req.setAttribute(RequestKeys.MESSAGE_KEY, "login failed");
        doForward(LOGIN_PAGE, req, resp);
    }

    private void doForward(String page, HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        RequestDispatcher dispatcher = req.getRequestDispatcher(page);
        dispatcher.forward(req,res);
    }
}