package cn.edu.xmu.yeahbuddy.service;

import cn.edu.xmu.yeahbuddy.domain.Team;
import cn.edu.xmu.yeahbuddy.domain.Tutor;
import cn.edu.xmu.yeahbuddy.domain.repo.TutorRepository;
import cn.edu.xmu.yeahbuddy.model.TutorDto;
import cn.edu.xmu.yeahbuddy.utils.UsernameAlreadyExistsException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class TutorService implements UserDetailsService, AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {

    @NonNls
    private static Log log = LogFactory.getLog(TutorService.class);

    private final YbPasswordEncodeService ybPasswordEncodeService;

    private final TutorRepository tutorRepository;

    /**
     * @param tutorRepository         Autowired
     * @param ybPasswordEncodeService Autowired
     */
    @Autowired
    public TutorService(TutorRepository tutorRepository, YbPasswordEncodeService ybPasswordEncodeService) {
        this.tutorRepository = tutorRepository;
        this.ybPasswordEncodeService = ybPasswordEncodeService;
    }

    @Contract(pure = true)
    public static Team asTutor(Object obj) {
        return ((Team) obj);
    }

    @Contract(pure = true)
    public static boolean isTutor(Object obj) {
        return obj instanceof Team;
    }

    /**
     * 查找导师 提供{@link UserDetailsService#loadUserByUsername(String)}
     *
     * @param username 查找的导师用户名
     * @return 导师
     * @throws UsernameNotFoundException 找不到导师
     */
    @Override
    @Transactional(readOnly = true)
    public Tutor loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Trying to load Tutor " + username);
        Tutor tutor = tutorRepository.findByName(username);
        if (tutor == null) {
            log.info("Failed to load Tutor " + username + ": not found");
            throw new UsernameNotFoundException(username);
        }
        log.debug("Loaded Tutor " + username);
        return tutor;
    }

    /**
     * 按ID查找导师
     *
     * @param id 查找的导师id
     * @return 导师
     * @throws UsernameNotFoundException 找不到导师
     */
    @Transactional(readOnly = true)
    Tutor loadTutorById(int id) throws UsernameNotFoundException {
        log.debug("Trying to load Tutor id " + id);
        Optional<Tutor> tutor = tutorRepository.findById(id);
        if (!tutor.isPresent()) {
            log.info("Failed to load Tutor id" + id + ": not found");
            throw new UsernameNotFoundException(Integer.toString(id));
        }
        log.debug("Loaded Tutor id " + id);
        return tutor.get();
    }

    /**
     * 查找导师 代理{@link TutorRepository#findByName(String)}
     *
     * @param name 查找的导师 用户名
     * @return 导师 或null
     */
    @Nullable
    @Transactional(readOnly = true)
    public Tutor findByName(String name) {
        return tutorRepository.findByName(name);
    }

    /**
     * 注册导师
     *
     * @param dto 导师DTO
     * @return 新注册的导师
     * @throws UsernameAlreadyExistsException 用户名已存在
     */
    @Transactional
    @PreAuthorize("hasAuthority('ManageTutor')")
    public Tutor registerNewTutor(TutorDto dto) throws UsernameAlreadyExistsException {
        log.debug("Trying to register new Tutor " + dto.getName());
        if (tutorRepository.findByName(dto.getName()) != null) {
            log.info("Failed to register Tutor " + dto.getName() + ": name already exist");
            throw new UsernameAlreadyExistsException("administrator.name.exist");
        }

        Tutor tutor = new Tutor(dto.getName(), ybPasswordEncodeService.encode(dto.getPassword()));
        tutor.setEmail(dto.getEmail());
        tutor.setPhone(dto.getPhone());
        Tutor result = tutorRepository.save(tutor);
        log.debug("Registered new Tutor " + result.toString());
        return result;
    }

    /**
     * 从PreAuthenticatedAuthenticationToken查找导师
     * 提供 {@link AuthenticationUserDetailsService#loadUserDetails(Authentication)}
     *
     * @param token {@link cn.edu.xmu.yeahbuddy.config.AuthTokenAuthenticationProvider#authenticate(Authentication)}
     * @return 导师
     */
    @Override
    public Tutor loadUserDetails(PreAuthenticatedAuthenticationToken token) {
        log.debug("Trying to load Tutor PreAuthenticatedAuthenticationToken " + token);
        return (Tutor) token.getPrincipal();
    }

    /**
     * 修改导师信息
     *
     * @param id  导师ID
     * @param dto 导师DTO
     * @return 修改后的导师
     * @throws UsernameAlreadyExistsException 如果修改用户名，用户名已存在
     */
    @Transactional
    @PreAuthorize("hasAuthority('ManageTutor') " +
                          "|| (T(cn.edu.xmu.yeahbuddy.service.TutorService).isTutor(principal) && T(cn.edu.xmu.yeahbuddy.service.TutorService).asTutor(principal).id == #id)")
    public Tutor updateTutor(int id, TutorDto dto) {
        Tutor tutor = tutorRepository.getOne(id);
        if (dto.getEmail() != null) {
            tutor.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            tutor.setPhone(dto.getPhone());
        }
        if (dto.getName() != null) {
            if (tutorRepository.findByName(dto.getName()) != null) {
                log.info("Fail to update Tutor " + tutor.getName() + ": name already exist");
                throw new UsernameAlreadyExistsException("tutor.name.exist");
            } else {
                tutor.setName(dto.getName());
            }
        }
        return tutorRepository.save(tutor);
    }

    /**
     * 修改导师密码
     *
     * @param id          导师ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @return 修改后的导师
     * @throws BadCredentialsException 原密码不正确
     */
    @Transactional
    @PreAuthorize("hasAuthority('ManageTutor') " +
                          "|| (T(cn.edu.xmu.yeahbuddy.service.TutorService).isTutor(principal) && T(cn.edu.xmu.yeahbuddy.service.TutorService).asTutor(principal).id == #id)")
    public Tutor updateTutorPassword(int id, CharSequence oldPassword, String newPassword) throws BadCredentialsException {
        Tutor tutor = tutorRepository.getOne(id);
        if (ybPasswordEncodeService.matches(oldPassword, tutor.getPassword())) {
            tutor.setPassword(ybPasswordEncodeService.encode(newPassword));
            return tutorRepository.save(tutor);
        } else {
            throw new BadCredentialsException("tutor.update.password");
        }
    }
}
