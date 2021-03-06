package org.rill.bpm.webclient.hello.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rill.bpm.webclient.hello.service.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/hello")
public class HelloController {
	
	private final String LIST_ACTION = "redirect:/web/hello";
	
	private HelloService helloService;
	
	public final HelloService getHelloService() {
		return helloService;
	}

	@Autowired
	public final void setHelloService(HelloService helloService) {
		this.helloService = helloService;
	}

	@RequestMapping(value={"/new"}, method=RequestMethod.GET)
	public ModelAndView _new(HttpServletRequest request, HttpServletResponse response, HelloVO command) {
		
		ModelAndView mav = new ModelAndView();
		command.setWhoSay("");
		mav.addObject("command", command);
        mav.setViewName("/hello/new");
        return mav;
	}
	
	@RequestMapping(value={"/delete"}, method=RequestMethod.GET)
	public ModelAndView _delete(HttpServletRequest request, HttpServletResponse response, HelloVO command) {
		
		ModelAndView mav = new ModelAndView();
		command.setWhoSay("");
		mav.addObject("command", command);
        mav.setViewName("/hello/delete");
        return mav;
	}
	
	@RequestMapping(method=RequestMethod.POST)
	public ModelAndView sayHello(HttpServletRequest request, HttpServletResponse response, HelloVO command) {
		
		getHelloService().sayHello(command.getWhoSay());
        return new ModelAndView(LIST_ACTION);
	}
	
	@RequestMapping(value={"/delete"}, method=RequestMethod.POST)
	public ModelAndView delete(HttpServletRequest request, HttpServletResponse response, HelloVO command) {
		
		getHelloService().deleteSayHello(command.getWhoSay());
        return new ModelAndView(LIST_ACTION);
	}
	
	@RequestMapping
	public ModelAndView list(HttpServletRequest request, HttpServletResponse response) {
		
		ModelAndView mav = new ModelAndView();
        mav.setViewName("/hello/list");
        mav.addObject("whoSaid", getHelloService().whoSaid());
        
        return mav;
	}
	
	// For apache AB create process instance.
	@RequestMapping(value={"/new_ab_rollback"}, method=RequestMethod.GET)
	public void _sayHelloRollback(HttpServletRequest request, HttpServletResponse response) {
		
		getHelloService().sayHello(new Integer(new Random().nextInt()).toString() + "_rollback");
		
	}
	@RequestMapping(value={"/new_ab"}, method=RequestMethod.GET)
	public void _sayHello(HttpServletRequest request, HttpServletResponse response) {
		
		getHelloService().sayHello(new Integer(new Random().nextInt()).toString());
		
	}
	@RequestMapping(value={"/new_ab_twice"}, method=RequestMethod.GET)
	public void _sayHelloTwice(HttpServletRequest request, HttpServletResponse response) {
		
		getHelloService().sayHello(new Integer(new Random().nextInt()).toString(), 2);
		
	}
	
	// For apache AB batch complete task instance.
	@RequestMapping(value={"/new_ab_batch"}, method=RequestMethod.GET)
	public void _sayHelloBatch(HttpServletRequest request, HttpServletResponse response) {
		
		int random = new Random().nextInt(10);
		random++;
		List<String> randomList = new ArrayList<String>(random);
		for (int i = 0; i < random; i++) {
			randomList.add("RandomList_" + i);
		}
		getHelloService().batchSayHello(randomList.toArray(new String[random]));
	}
	
	@RequestMapping(value={"/new_ab_2"}, method=RequestMethod.GET)
	public void _sayHello2(HttpServletRequest request, HttpServletResponse response) {
		
		getHelloService().batchSayHello(new String[] {"new_ab_2", "new_ab_2"});
	}
	
}
