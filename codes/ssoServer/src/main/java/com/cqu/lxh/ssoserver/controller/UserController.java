package com.cqu.lxh.ssoserver.controller;

import com.cqu.lxh.ssoserver.model.ClientInfo;
import com.cqu.lxh.ssoserver.model.User;
import com.cqu.lxh.ssoserver.service.HttpService;
import com.cqu.lxh.ssoserver.service.MockDatabaseService;
import com.cqu.lxh.ssoserver.service.SSOServerService;
import com.cqu.lxh.ssoserver.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpSession;
import java.util.*;

@Controller
public class UserController {
    public static final String KEY_USER = "__user__";
    final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    UserService userService;

    @ExceptionHandler(RuntimeException.class)
    public ModelAndView handleUnknowException(Exception ex) {
        logger.error(ex.getClass().getSimpleName());
        logger.error(ex.getMessage());
        return new ModelAndView("500.html", Map.of("error", ex.getClass().getSimpleName(), "message", ex.getMessage()));
    }

    @GetMapping("/")
    public ModelAndView index(HttpSession session) {
        User user = (User) session.getAttribute(KEY_USER);
        Map<String, Object> model = new HashMap<>();
        if (user != null) {
            model.put("user", model);
        }
        return new ModelAndView("index.html", model);
    }

    @GetMapping("/register")
    public ModelAndView register() {
        return new ModelAndView("register.html");
    }

    @PostMapping("/register")
    public ModelAndView doRegister(@RequestParam("email") String email, @RequestParam("password") String password,
                                   @RequestParam("name") String name) {
        try {
            User user = userService.register(email, password, name);
            logger.info("user registered: {}", user.getEmail());
        } catch (RuntimeException e) {
            return new ModelAndView("register.html", Map.of("email", email, "error", "Register failed"));
        }
        return new ModelAndView("redirect:/login", Map.of("email", email));
    }

    @GetMapping("/checkLogin")
    public ModelAndView checkLogin(String redirectUrl, HttpSession session) {
        logger.info("checkLogin from: {}", redirectUrl);
        //1.??????????????????????????????
        String token = (String) session.getAttribute("token");
        logger.info("check token: {}", token);
        if (!StringUtils.hasLength(token)) { // TODO ????????????token?????????
            //????????????????????????
            //??????????????????????????????????????????.
            logger.info("failed to checkLogin ");
            return new ModelAndView("login.html", Map.of("redirectUrl", redirectUrl));
        } else {
            //???????????????
            //??????????????????,????????????redirectUrl,???????????????  http://www.A.com:8088/main?token
            logger.info("checkLogin successfully");
            User user = (User) session.getAttribute(KEY_USER);
//            return "redirect:" + redirectUrl + "?token=" + token; ?????????????????????model
            Map<String, Object> model = new HashMap<>();
            if (user != null)
                model.put("user", user);
            model.put("token", token);
            return new ModelAndView("redirect:" + redirectUrl, model);
        }
    }

    /**
     * ??????token????????????????????????????????????
     */
    @RequestMapping("/verify")
    @ResponseBody
    public String verifyToken(String token, String clientUrl, String jsessionid) {
        logger.info("verifyToken {} from: {}", token, clientUrl);
        if (MockDatabaseService.T_TOKEN.contains(token)) {
            //???????????????????????????????????????????????????
            List<ClientInfo> clientInfoList = MockDatabaseService.T_CLIENT_INFO.get(token);
            if (clientInfoList == null) {
                clientInfoList = new ArrayList<ClientInfo>();
                MockDatabaseService.T_CLIENT_INFO.put(token, clientInfoList);
            }
            ClientInfo info = new ClientInfo();
            info.setClientUrl(clientUrl);
            info.setJsessionid(jsessionid);
            clientInfoList.add(info);
            //????????????,??????true
            logger.info("succeed to verifyToken from: {} ", clientUrl);
            return "true";
        }
        logger.info("failed to verifyToken from: {}", clientUrl);
        return "false";
    }

    @GetMapping("/login")
    public ModelAndView login(String email, HttpSession session) {
        User user = (User) session.getAttribute(KEY_USER);
        if (user != null)
            return new ModelAndView("redirect:/profile");
        else {
            if (StringUtils.hasLength(email))
                return new ModelAndView("login.html", Map.of("email", email));
            else
                return new ModelAndView("login.html");
        }
    }


    @PostMapping("/login")
    public ModelAndView doLogin(@RequestParam("email") String email, @RequestParam("password") String password,
                                @RequestParam("redirectUrl") String redirectUrl, HttpSession session) {
        logger.info("doLogin from: {}", redirectUrl);
        try {
            User user = userService.login(email, password);
            //??????????????????
            //1.??????????????????
            String token = UUID.randomUUID().toString();
            //2.?????????????????????,???????????????????????????????????????.
            session.setAttribute(KEY_USER, user);
            session.setAttribute("token", token);
            //3.???????????????????????????????????????.
            MockDatabaseService.T_TOKEN.add(token);
            //4.????????????redirectUrl,?????????????????????.  http://www.crm.com:8088/main?token=
            logger.info("doLogin success, token: {}", token);
            Map<String, Object> model = new HashMap<>();
            if (user != null)
                model.put("user", user);
            if (StringUtils.hasLength(redirectUrl)) {
                //  model.addAttribute("token", token); ?????????????????????????????????
                // "redirect:" + redirectUrl + "?token=" + token;
                model.put("token", token);
                return new ModelAndView("redirect:" + redirectUrl, model);
            } else
                return new ModelAndView("redirect:/profile", model);
        } catch (RuntimeException e) {
            ModelAndView mv = new ModelAndView("login.html");// ????????????html
            mv.addObject("email", email);
            mv.addObject("error", "?????????????????????????????????");
            mv.addObject("redirectUrl", redirectUrl);
            logger.info("failed to doLogin");
            return mv;
        }
    }

    @GetMapping("/profile")
    public ModelAndView profile(HttpSession session) {
        logger.info("show profile");
        User user = (User) session.getAttribute(KEY_USER);
        if (user == null) {
            return new ModelAndView("redirect:/login");
        }
        return new ModelAndView("profile.html", Map.of("user", user));
    }

    @GetMapping("/logout")
    public ModelAndView logout(HttpSession session) {
        logger.info("log out global session}");
        String token = (String) session.getAttribute("token");
        //??????t_token???????????????
        MockDatabaseService.T_TOKEN.remove(token);
        List<ClientInfo> clientInfoList = MockDatabaseService.T_CLIENT_INFO.remove(token);
        try {
            for (ClientInfo info : clientInfoList) {
                //???????????????????????????,???????????????????????????????????????
                HttpService.sendHttpRequest(info.getClientUrl(), info.getJsessionid());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        session.invalidate();
        return new ModelAndView("redirect:/login");
    }

    @GetMapping("/ainfo")
    public ModelAndView ainfo() {
        return new ModelAndView("redirect:" + SSOServerService.CLIENTA_HOST_URL + "/ainfo");
    }

    @GetMapping("/binfo")
    public ModelAndView binfo() {
        return new ModelAndView("redirect:" + SSOServerService.CLIENTB_HOST_URL + "/binfo");
    }
}
