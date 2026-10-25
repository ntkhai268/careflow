package com.careflow.appointment.service;

import com.careflow.appointment.client.DirectoryClient;
import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RoomScheduleService {
    public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DirectoryClient directory;
    private final int defaultCapacity;
    public RoomScheduleService(JdbcTemplate jdbc,ObjectMapper json,DirectoryClient directory,
                               @Value("${appointment.max-appointments-per-slot:5}") int defaultCapacity){
        this.jdbc=jdbc;this.json=json;this.directory=directory;this.defaultCapacity=Math.max(1,defaultCapacity);
    }
    public record Window(String start,String end,int slotMinutes,int capacity){}
    public record ExceptionDay(LocalDate date,boolean closed,List<Window> windows){}
    public record Definition(LocalDate effectiveFrom,String timeZone,List<Integer> weekdays,List<Window> windows,List<ExceptionDay> exceptions){}
    public record Update(long expectedVersion,Definition definition){}
    public record Revision(long version,LocalDate effectiveFrom,Instant createdAt){}
    public record View(String roomId,long version,Definition definition,List<Revision> history){}
    public record Slot(String timeSlot,int capacity){}

    public static Definition defaults(int capacity){
        return new Definition(LocalDate.of(1900,1,1),ZONE.getId(),List.of(1,2,3,4,5,6,7),
                List.of(new Window("07:30","11:30",30,capacity),new Window("13:30","16:30",30,capacity)),List.of());
    }
    @Transactional(readOnly=true)
    public View get(String roomId){
        requireRoom(roomId);
        var rows=jdbc.queryForList("SELECT version,definition::text FROM appointment_room_schedules WHERE room_id=?",roomId);
        if(rows.isEmpty())return new View(roomId,0,defaults(defaultCapacity),List.of());
        var history=jdbc.query("SELECT version,effective_from,created_at FROM appointment_schedule_revisions WHERE room_id=? ORDER BY version DESC LIMIT 20",
                (rs,n)->new Revision(rs.getLong(1),rs.getDate(2).toLocalDate(),rs.getTimestamp(3).toInstant()),roomId);
        return new View(roomId,((Number)rows.getFirst().get("version")).longValue(),decode((String)rows.getFirst().get("definition")),history);
    }
    @Transactional
    public View save(String roomId,Update request,UUID actor){
        if(actor==null)throw new BusinessException(401,"Thiếu người quản trị.");
        requireRoom(roomId);
        if(request==null || request.expectedVersion()<0)throw new BusinessException(400,"Phiên bản lịch không hợp lệ.");
        Definition definition=validate(request.definition());
        if(definition.effectiveFrom().isBefore(LocalDate.now(ZONE)))throw new BusinessException(400,"Ngày hiệu lực phải từ hôm nay.");
        lockRooms(List.of(roomId));
        Long current=jdbc.queryForObject("SELECT coalesce((SELECT version FROM appointment_room_schedules WHERE room_id=?),0)",Long.class,roomId);
        if(current==null || current!=request.expectedVersion())throw new BusinessException(409,"Lịch đã được thay đổi. Hãy tải lại trước khi lưu.");
        // The booking transaction acquires the same room lock before reserving any slot.
        var booked=jdbc.queryForList("SELECT appointment_date,time_slot,count(*) AS total FROM appointments WHERE room_id=? AND appointment_date>=? AND status<>'CANCELLED' GROUP BY appointment_date,time_slot",roomId,definition.effectiveFrom());
        for(var item:booked){
            LocalDate date=((java.sql.Date)item.get("appointment_date")).toLocalDate();
            String slot=AppointmentService.normalizeTimeSlot((String)item.get("time_slot"));
            int capacity=slots(definition,date).stream().filter(s->s.timeSlot().equals(slot)).mapToInt(Slot::capacity).findFirst().orElse(0);
            if(capacity<((Number)item.get("total")).intValue())throw new BusinessException(409,"Lịch mới ảnh hưởng lịch khám đã đặt ngày "+date+" ca "+slot+". Chọn ngày hiệu lực khác hoặc giữ khung giờ/sức chứa.");
        }
        long next=current+1;String document=encode(definition);
        jdbc.update("INSERT INTO appointment_room_schedules(room_id,version,effective_from,definition,updated_by) VALUES (?,?,?,?::jsonb,?) ON CONFLICT(room_id) DO UPDATE SET version=EXCLUDED.version,effective_from=EXCLUDED.effective_from,definition=EXCLUDED.definition,updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP",roomId,next,definition.effectiveFrom(),document,actor);
        jdbc.update("INSERT INTO appointment_schedule_revisions(room_id,version,effective_from,definition,created_by) VALUES (?,?,?,?::jsonb,?)",roomId,next,definition.effectiveFrom(),document,actor);
        return get(roomId);
    }
    public void lockRooms(Collection<String> rooms){
        rooms.stream().distinct().sorted().forEach(room->jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","appointment-schedule:"+room));
    }
    public List<Slot> slots(String roomId,LocalDate date){
        var documents=jdbc.queryForList("SELECT definition::text FROM appointment_schedule_revisions WHERE room_id=? AND effective_from<=? ORDER BY version DESC LIMIT 1",roomId,date);
        return slots(documents.isEmpty()?defaults(defaultCapacity):decode((String)documents.getFirst().get("definition")),date);
    }
    public static List<Slot> slots(Definition definition,LocalDate date){
        var exception=definition.exceptions().stream().filter(e->e.date().equals(date)).findFirst().orElse(null);
        if(exception!=null && exception.closed())return List.of();
        if(exception==null && !definition.weekdays().contains(date.getDayOfWeek().getValue()))return List.of();
        List<Window> windows=exception!=null && exception.windows()!=null && !exception.windows().isEmpty()?exception.windows():definition.windows();
        var result=new ArrayList<Slot>();
        for(Window window:windows){
            LocalTime end=LocalTime.parse(window.end());
            for(LocalTime start=LocalTime.parse(window.start());start.isBefore(end);){
                LocalTime next=start.plusMinutes(window.slotMinutes());
                result.add(new Slot(start.format(DateTimeFormatter.ofPattern("HH:mm"))+"-"+next.format(DateTimeFormatter.ofPattern("HH:mm")),window.capacity()));start=next;
            }
        }
        return result.stream().sorted(Comparator.comparing(Slot::timeSlot)).toList();
    }
    public static Definition validate(Definition definition){
        if(definition==null || definition.effectiveFrom()==null || !ZONE.getId().equals(definition.timeZone())
                || definition.weekdays()==null || definition.weekdays().size()>7 || new HashSet<>(definition.weekdays()).size()!=definition.weekdays().size()
                || definition.weekdays().stream().anyMatch(day->day==null || day<1 || day>7))throw new BusinessException(400,"Ngày làm việc hoặc múi giờ không hợp lệ.");
        validateWindows(definition.windows());
        List<ExceptionDay> exceptions=definition.exceptions()==null?List.of():definition.exceptions();
        if(exceptions.size()>366)throw new BusinessException(400,"Tối đa366 ngày ngoại lệ.");
        var dates=new HashSet<LocalDate>();
        for(ExceptionDay exception:exceptions){
            if(exception==null || exception.date()==null || exception.date().isBefore(definition.effectiveFrom()) || !dates.add(exception.date()))throw new BusinessException(400,"Ngày ngoại lệ bị trùng hoặc nằm trước hiệu lực.");
            if(exception.closed() && exception.windows()!=null && !exception.windows().isEmpty())throw new BusinessException(400,"Ngày nghỉ không được có khung khám.");
            if(exception.windows()!=null && !exception.windows().isEmpty())validateWindows(exception.windows());
        }
        return new Definition(definition.effectiveFrom(),ZONE.getId(),List.copyOf(definition.weekdays()),List.copyOf(definition.windows()),List.copyOf(exceptions));
    }
    private static void validateWindows(List<Window> windows){
        if(windows==null || windows.isEmpty() || windows.size()>8)throw new BusinessException(400,"Chọn từ1 đến8 khoảng làm việc.");
        var sorted=new ArrayList<Window>(windows);
        for(Window w:windows){
            if(w==null || w.start()==null || w.end()==null || !w.start().matches("\\d{2}:\\d{2}") || !w.end().matches("\\d{2}:\\d{2}") || w.slotMinutes()<5 || w.slotMinutes()>120 || w.capacity()<1 || w.capacity()>200)throw new BusinessException(400,"Thời gian, thời lượng ca hoặc sức chứa không hợp lệ.");
            try{
                long minutes=Duration.between(LocalTime.parse(w.start()),LocalTime.parse(w.end())).toMinutes();
                if(minutes<=0 || minutes%w.slotMinutes()!=0)throw new BusinessException(400,"Khoảng làm việc phải chia hết cho thời lượng ca, không qua ngày.");
            }catch(java.time.format.DateTimeParseException invalid){throw new BusinessException(400,"Giờ phải theo định dạng HH:mm hợp lệ.");}
        }
        sorted.sort(Comparator.comparing(Window::start));
        for(int i=1;i<sorted.size();i++)if(sorted.get(i).start().compareTo(sorted.get(i-1).end())<0)throw new BusinessException(400,"Các khoảng làm việc bị chồng nhau.");
    }
    private void requireRoom(String roomId){
        if(roomId==null || !roomId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,49}"))throw new BusinessException(400,"Phòng không hợp lệ.");
        try{
            var response=directory.getAllRooms();
            if(response==null || response.getData()==null)throw new BusinessException(503,"Chưa xác minh được danh mục phòng.");
            if(response.getData().stream().noneMatch(room->roomId.equals(room.getId()) && Boolean.TRUE.equals(room.getIsActive()) && "CONSULTATION".equals(room.getRoomType())))throw new BusinessException(409,"Chọn phòng khám đang hoạt động trong Directory.");
        }catch(feign.FeignException unavailable){throw new BusinessException(503,"Không thể xác minh phòng khám.");}
    }
    private String encode(Definition definition){try{return json.writeValueAsString(definition);}catch(Exception error){throw new IllegalStateException(error);}}
    private Definition decode(String document){try{return validate(json.readValue(document,Definition.class));}catch(Exception error){throw new IllegalStateException("Invalid persisted room schedule",error);}}
    @Transactional
    public void seedDemo(){
        Definition definition=defaults(defaultCapacity);String document=encode(definition);
        int inserted=jdbc.update("INSERT INTO appointment_room_schedules(room_id,version,effective_from,definition) VALUES ('ROOM-01',1,?,?::jsonb) ON CONFLICT(room_id) DO NOTHING",definition.effectiveFrom(),document);
        if(inserted==1)jdbc.update("INSERT INTO appointment_schedule_revisions(room_id,version,effective_from,definition) VALUES ('ROOM-01',1,?,?::jsonb)",definition.effectiveFrom(),document);
    }
}
