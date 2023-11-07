package kr.co.imguru.domain.skill.service;

import kr.co.imguru.domain.skill.dto.SkillCreateDto;
import kr.co.imguru.domain.skill.dto.SkillReadDto;
import kr.co.imguru.domain.skill.dto.SkillUpdateDto;
import kr.co.imguru.domain.skill.entity.Skill;
import kr.co.imguru.domain.skill.repository.SkillRepository;
import kr.co.imguru.global.exception.DuplicatedException;
import kr.co.imguru.global.exception.NotFoundException;
import kr.co.imguru.global.model.ResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final SkillRepository skillRepository;

    @Override
    public void createSkill(SkillCreateDto createDto) {
        isSkillName(createDto.getName());

        skillRepository.save(toEntity(createDto));
    }

    @Override
    public SkillReadDto getSkill(String skillName) {
        Optional<Skill> skill = skillRepository.findByNameAndIsDeleteFalse(skillName);

        isSkill(skill);

        return toReadDto(skill.get());
    }

    @Override
    public List<SkillReadDto> getAllSkills() {
        List<Skill> skillList = skillRepository.findAllByIsDeleteFalse();

        return skillList.stream().map(this::toReadDto).toList();
    }

    @Override
    public SkillReadDto updateSkill(String skillName, SkillUpdateDto updateDto) {
        Optional<Skill> skill = skillRepository.findByNameAndIsDeleteFalse(skillName);

        isSkill(skill);

        skill.get().changeSkill(updateDto);

        skillRepository.save(skill.get());

        return toReadDto(skill.get());
    }

    @Override
    public void deleteSkill(String skillName) {
        Optional<Skill> skill = skillRepository.findByNameAndIsDeleteFalse(skillName);

        isSkill(skill);

        skill.get().changeDeleteAt();

        skillRepository.save(skill.get());
    }

    private void isSkill(Optional<Skill> skill) {
        if (skill.isEmpty()) {
            throw new NotFoundException(ResponseStatus.FAIL_SKILL_NOT_FOUND);
        }
    }

    private void isSkillName(String skillName) {
        if (skillRepository.existsByNameAndIsDeleteFalse(skillName)) {
            throw new DuplicatedException(ResponseStatus.FAIL_SKILL_NAME_DUPLICATED);
        }
    }

    private Skill toEntity(SkillCreateDto dto) {
        return Skill.builder()
                .name(dto.getName())
                .build();
    }

    private SkillReadDto toReadDto(Skill skill) {
        return SkillReadDto.builder()
                .SkillId(skill.getId())
                .name(skill.getName())
                .build();
    }
}
