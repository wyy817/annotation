package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wsi.platform.entity.Workspace;
import com.wsi.platform.mapper.WorkspaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;

    public List<Workspace> listAll() {
        return workspaceMapper.selectList(
                new LambdaQueryWrapper<Workspace>().orderByDesc(Workspace::getCreatedAt));
    }

    public Workspace getById(Long id) {
        return workspaceMapper.selectById(id);
    }

    public Workspace create(String name, String description) {
        Workspace ws = new Workspace();
        ws.setName(name);
        ws.setDescription(description);
        workspaceMapper.insert(ws);
        return ws;
    }

    public Workspace update(Long id, String name, String description) {
        Workspace ws = workspaceMapper.selectById(id);
        if (ws == null) return null;
        ws.setName(name);
        ws.setDescription(description);
        workspaceMapper.updateById(ws);
        return ws;
    }

    public boolean delete(Long id) {
        return workspaceMapper.deleteById(id) > 0;
    }
}
