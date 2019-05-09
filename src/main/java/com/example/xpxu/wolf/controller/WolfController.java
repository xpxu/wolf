package com.example.xpxu.wolf.controller;

import com.example.xpxu.wolf.dto.DemoDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DATE 2018/9/28.
 *
 * @author xupeng.
 */
@RestController
public class WolfController {

    @GetMapping("/hello")
    public DemoDTO hello() throws Exception {
//        int randomNum = ThreadLocalRandom.current().nextInt(50, 200);
        Thread.sleep(100000);

        DemoDTO demoDTO = new DemoDTO();
        demoDTO.setName("have a good day");
        return demoDTO;
    }
}
