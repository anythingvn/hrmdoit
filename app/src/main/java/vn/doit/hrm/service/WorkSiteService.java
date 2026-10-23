package vn.doit.hrm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.dto.WorkSiteDTO;
import vn.doit.hrm.service.mapper.WorkSiteMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.WorkSite}.
 */
@Service
@Transactional
public class WorkSiteService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkSiteService.class);

    private final WorkSiteRepository workSiteRepository;

    private final WorkSiteMapper workSiteMapper;

    public WorkSiteService(WorkSiteRepository workSiteRepository, WorkSiteMapper workSiteMapper) {
        this.workSiteRepository = workSiteRepository;
        this.workSiteMapper = workSiteMapper;
    }

    /**
     * Save a workSite.
     *
     * @param workSiteDTO the entity to save.
     * @return the persisted entity.
     */
    public WorkSiteDTO save(WorkSiteDTO workSiteDTO) {
        LOG.debug("Request to save WorkSite : {}", workSiteDTO);
        normalizeAndValidateLocation(workSiteDTO);
        WorkSite workSite = workSiteMapper.toEntity(workSiteDTO);
        workSite = workSiteRepository.save(workSite);
        return workSiteMapper.toDto(workSite);
    }

    /**
     * Update a workSite.
     *
     * @param workSiteDTO the entity to save.
     * @return the persisted entity.
     */
    public WorkSiteDTO update(WorkSiteDTO workSiteDTO) {
        LOG.debug("Request to update WorkSite : {}", workSiteDTO);
        normalizeAndValidateLocation(workSiteDTO);
        WorkSite workSite = workSiteMapper.toEntity(workSiteDTO);
        workSite = workSiteRepository.save(workSite);
        return workSiteMapper.toDto(workSite);
    }

    /**
     * Partially update a workSite.
     *
     * @param workSiteDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<WorkSiteDTO> partialUpdate(WorkSiteDTO workSiteDTO) {
        LOG.debug("Request to partially update WorkSite : {}", workSiteDTO);

        return workSiteRepository
            .findById(workSiteDTO.getId())
            .map(existingWorkSite -> {
                workSiteMapper.partialUpdate(existingWorkSite, workSiteDTO);
                WorkSiteDTO merged = workSiteMapper.toDto(existingWorkSite);
                normalizeAndValidateLocation(merged);
                existingWorkSite.setOfficeIps(merged.getOfficeIps());

                return existingWorkSite;
            })
            .map(workSiteRepository::save)
            .map(workSiteMapper::toDto);
    }

    /**
     * Get all the workSites.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<WorkSiteDTO> findAll() {
        LOG.debug("Request to get all WorkSites");
        return workSiteRepository.findAll().stream().map(workSiteMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one workSite by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<WorkSiteDTO> findOne(Long id) {
        LOG.debug("Request to get WorkSite : {}", id);
        return workSiteRepository.findById(id).map(workSiteMapper::toDto);
    }

    /**
     * Delete the workSite by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete WorkSite : {}", id);
        workSiteRepository.deleteById(id);
    }

    private static final BigDecimal MAX_LAT = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LON = BigDecimal.valueOf(180);
    private static final Pattern IP_CHARS = Pattern.compile("[0-9a-fA-F.:/]+");
    private static final Pattern IPV4 = Pattern.compile("(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");
    private static final Pattern PREFIX = Pattern.compile("\\d{1,3}");

    /** Kiểm tra toạ độ, bán kính, danh sách IP; đặt bán kính mặc định 200 và chuẩn hoá office_ips (mỗi dòng một mục, đã cắt khoảng trắng). */
    private static void normalizeAndValidateLocation(WorkSiteDTO dto) {
        BigDecimal lat = dto.getLatitude();
        BigDecimal lon = dto.getLongitude();
        if ((lat == null) != (lon == null)) {
            throw new EmployeeRuleException("Cần nhập cả vĩ độ và kinh độ, hoặc để trống cả hai.", "worksitelocationinvalid");
        }
        if (lat != null && (lat.abs().compareTo(MAX_LAT) > 0 || lon.abs().compareTo(MAX_LON) > 0)) {
            throw new EmployeeRuleException("Vĩ độ trong khoảng -90..90, kinh độ trong khoảng -180..180.", "worksitelocationinvalid");
        }
        if (dto.getRadiusM() == null) {
            dto.setRadiusM(200);
        } else if (dto.getRadiusM() < 20 || dto.getRadiusM() > 5000) {
            throw new EmployeeRuleException("Bán kính chấm công từ 20 đến 5000 mét.", "worksiteradiusinvalid");
        }
        List<String> ips = parseOfficeIps(dto.getOfficeIps());
        for (String ip : ips) {
            if (!isValidIpOrCidr(ip)) {
                throw new EmployeeRuleException("Dải IP không hợp lệ: " + ip, "worksiteipinvalid");
            }
        }
        dto.setOfficeIps(ips.isEmpty() ? null : String.join("\n", ips));
    }

    /** Tách chuỗi office_ips thành danh sách IP/CIDR: mỗi dòng một mục, cắt khoảng trắng, bỏ dòng trống. Không kiểm tra hợp lệ. */
    public static List<String> parseOfficeIps(String officeIps) {
        List<String> result = new ArrayList<>();
        if (officeIps == null) {
            return result;
        }
        for (String line : officeIps.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                result.add(t);
            }
        }
        return result;
    }

    /** IPv4/IPv6 hoặc CIDR; chỉ chấp nhận chữ số hex, '.', ':' và '/' nên không bao giờ gọi DNS. */
    static boolean isValidIpOrCidr(String value) {
        if (!IP_CHARS.matcher(value).matches()) {
            return false;
        }
        String addr = value;
        String prefix = null;
        int slash = value.indexOf('/');
        if (slash >= 0) {
            addr = value.substring(0, slash);
            prefix = value.substring(slash + 1);
            if (!PREFIX.matcher(prefix).matches()) {
                return false;
            }
        }
        boolean v6 = addr.indexOf(':') >= 0;
        if (v6) {
            try {
                java.net.InetAddress.getByName(addr);
            } catch (java.net.UnknownHostException | RuntimeException e) {
                return false;
            }
        } else if (!IPV4.matcher(addr).matches()) {
            return false;
        }
        return prefix == null || Integer.parseInt(prefix) <= (v6 ? 128 : 32);
    }
}
