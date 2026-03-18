package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wsi.platform.entity.Annotation;
import com.wsi.platform.entity.Label;
import com.wsi.platform.mapper.AnnotationMapper;
import com.wsi.platform.mapper.LabelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LabelService {

    private final LabelMapper labelMapper;
    private final AnnotationMapper annotationMapper;

    public List<Label> listBySlide(Long slideId) {
        return labelMapper.selectList(
                new LambdaQueryWrapper<Label>()
                        .eq(Label::getSlideId, slideId)
                        .orderByAsc(Label::getClassIndex));
    }

    public Label create(Long slideId, String name, String color) {
        List<Label> existing = listBySlide(slideId);
        int nextIndex = existing.stream()
                .mapToInt(l -> l.getClassIndex() == null ? 0 : l.getClassIndex())
                .max().orElse(0) + 1;

        Label label = new Label();
        label.setSlideId(slideId);
        label.setName(name);
        label.setColor(color);
        label.setClassIndex(nextIndex);
        labelMapper.insert(label);
        return label;
    }

    public Label update(Long id, String name, String color) {
        Label label = labelMapper.selectById(id);
        if (label == null) return null;
        if (name != null) label.setName(name);
        if (color != null) label.setColor(color);
        labelMapper.updateById(label);
        return label;
    }

    /** Delete label and cascade-delete its annotations. */
    @Transactional
    public boolean delete(Long id) {
        if (labelMapper.selectById(id) == null) return false;
        annotationMapper.delete(new LambdaQueryWrapper<Annotation>()
                .eq(Annotation::getLabelId, id));
        return labelMapper.deleteById(id) > 0;
    }
}
