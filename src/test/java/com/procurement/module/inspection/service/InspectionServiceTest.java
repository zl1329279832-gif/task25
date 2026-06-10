package com.procurement.module.inspection.service;

import com.procurement.common.enums.DeliveryStatus;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.delivery.service.DeliveryService;
import com.procurement.module.inspection.dto.InspectionCreateDTO;
import com.procurement.module.inspection.entity.Inspection;
import com.procurement.module.inspection.mapper.InspectionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock private InspectionMapper inspectionMapper;
    @Mock private DeliveryService deliveryService;
    @Mock private CodeGenerator codeGenerator;

    @InjectMocks
    private InspectionService service;

    @Test
    void create_shouldUpdateDeliveryToAccepted_whenQualified() {
        when(codeGenerator.generate("INS")).thenReturn("INS-20260101-0001");
        when(inspectionMapper.insert(any())).thenReturn(1);

        InspectionCreateDTO dto = new InspectionCreateDTO();
        dto.setDeliveryId(1L);
        dto.setDeliveryItemId(1L);
        dto.setMaterialId(1L);
        dto.setInspectQuantity(new BigDecimal("100"));
        dto.setQualifiedQuantity(new BigDecimal("100"));
        dto.setResult("QUALIFIED");

        service.create(dto, 1L);

        verify(deliveryService).updateStatus(1L, DeliveryStatus.ACCEPTED.name());
    }

    @Test
    void create_shouldUpdateDeliveryToRejected_whenUnqualified() {
        when(codeGenerator.generate("INS")).thenReturn("INS-20260101-0002");
        when(inspectionMapper.insert(any())).thenReturn(1);

        InspectionCreateDTO dto = new InspectionCreateDTO();
        dto.setDeliveryId(2L);
        dto.setDeliveryItemId(2L);
        dto.setMaterialId(1L);
        dto.setInspectQuantity(new BigDecimal("100"));
        dto.setQualifiedQuantity(BigDecimal.ZERO);
        dto.setResult("UNQUALIFIED");

        service.create(dto, 1L);

        verify(deliveryService).updateStatus(2L, DeliveryStatus.REJECTED.name());
    }

    @Test
    void create_shouldUpdateDeliveryToPartialAccepted_whenConcession() {
        when(codeGenerator.generate("INS")).thenReturn("INS-20260101-0003");
        when(inspectionMapper.insert(any())).thenReturn(1);

        InspectionCreateDTO dto = new InspectionCreateDTO();
        dto.setDeliveryId(3L);
        dto.setDeliveryItemId(3L);
        dto.setMaterialId(1L);
        dto.setInspectQuantity(new BigDecimal("100"));
        dto.setQualifiedQuantity(new BigDecimal("80"));
        dto.setResult("CONCESSION_ACCEPT");

        service.create(dto, 1L);

        verify(deliveryService).updateStatus(3L, DeliveryStatus.PARTIAL_ACCEPTED.name());
    }

    @Test
    void create_shouldCalculateUnqualifiedQuantity_whenNotProvided() {
        when(codeGenerator.generate("INS")).thenReturn("INS-20260101-0004");
        when(inspectionMapper.insert(any())).thenReturn(1);

        InspectionCreateDTO dto = new InspectionCreateDTO();
        dto.setDeliveryId(1L);
        dto.setDeliveryItemId(1L);
        dto.setMaterialId(1L);
        dto.setInspectQuantity(new BigDecimal("100"));
        dto.setQualifiedQuantity(new BigDecimal("85"));
        dto.setUnqualifiedQuantity(null); // not provided
        dto.setResult("CONCESSION_ACCEPT");

        service.create(dto, 1L);

        ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
        verify(inspectionMapper).insert(captor.capture());
        assertEquals(new BigDecimal("15"), captor.getValue().getUnqualifiedQuantity());
    }
}
