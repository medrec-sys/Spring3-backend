package fun.medrec.spring.controller;

import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.service.AgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {
    @Autowired
    private AgentService agentService;

    @RequestMapping("/list")
    public Result<PageVO<Agent>> getPage(@RequestBody PageDTO<Agent> page) {
        return Result.success(agentService.getPage(page));
    }

    @RequestMapping("/{id}")
    public Result<Agent> getById(@PathVariable String id) {
        return Result.success(agentService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        return agentService.removeById(id) ? Result.success() : Result.error("删除失败");
    }

    @PostMapping
    public Result<Void> add(@RequestBody Agent agent) {
        return agentService.save(agent) ? Result.success() : Result.error("添加失败");
    }

    @PutMapping
    public Result<Void> update(@RequestBody Agent agent) {
        return agentService.updateById(agent) ? Result.success() : Result.error("更新失败");
    }
}
