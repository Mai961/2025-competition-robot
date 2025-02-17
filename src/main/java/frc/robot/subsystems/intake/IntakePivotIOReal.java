package frc.robot.subsystems.intake;

import com.ctre.phoenix6.BaseStatusSignal;
import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.configs.MotionMagicConfigs;
import com.ctre.phoenix6.configs.Slot0Configs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.MotionMagicVoltage;
import com.ctre.phoenix6.controls.VoltageOut;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.GravityTypeValue;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;
import edu.wpi.first.units.Units;
import edu.wpi.first.units.measure.*;
import frc.robot.RobotConstants;

import static frc.robot.RobotConstants.IntakeConstants.*;

public class IntakePivotIOReal implements IntakePivotIO {
    private final TalonFX motor = new TalonFX(RobotConstants.IntakeConstants.INTAKE_PIVOT_MOTOR_ID,
            RobotConstants.CANIVORE_CAN_BUS_NAME);

    private final StatusSignal<AngularVelocity> velocityRotPerSec = motor.getVelocity();
    private final StatusSignal<Voltage> appliedVolts = motor.getSupplyVoltage();
    private final StatusSignal<Current> statorCurrentAmps = motor.getStatorCurrent();
    private final StatusSignal<Current> supplyCurrentAmps = motor.getSupplyCurrent();
    private final StatusSignal<Temperature> tempCelsius = motor.getDeviceTemp();
    private final StatusSignal<Angle> currentPositionRot = motor.getPosition();


    private final VoltageOut voltageOut = new VoltageOut(0.0).withEnableFOC(true);
    private final MotionMagicVoltage motionMagic = new MotionMagicVoltage(0.0).withEnableFOC(true);
    private final MotionMagicConfigs motionMagicConfigs;

    double targetAngleDeg = 0.0;

    public IntakePivotIOReal() {
        var config = new TalonFXConfiguration();
        config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
        config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;
        config.Slot0.GravityType = GravityTypeValue.Arm_Cosine;
        config.CurrentLimits.SupplyCurrentLimit = 40.0;
        config.CurrentLimits.SupplyCurrentLimitEnable = true;
        config.CurrentLimits.StatorCurrentLimit = 40.0;
        config.CurrentLimits.StatorCurrentLimitEnable = true;

        motor.setPosition(angleToTalonPos(5));

        motor.getConfigurator().apply(new Slot0Configs()
                .withKP(IntakePivotGainsClass.INTAKE_PIVOT_KP.get())
                .withKI(IntakePivotGainsClass.INTAKE_PIVOT_KI.get())
                .withKD(IntakePivotGainsClass.INTAKE_PIVOT_KD.get()));

        motionMagicConfigs = new MotionMagicConfigs();
        motionMagicConfigs.MotionMagicCruiseVelocity = INTAKE_PIVOT_CRUISE_VELOCITY.get();
        motionMagicConfigs.MotionMagicAcceleration = INTAKE_PIVOT_ACCELERATION.get();
        motionMagicConfigs.MotionMagicJerk = INTAKE_PIVOT_JERK.get();
        config.withMotionMagic(motionMagicConfigs);

        motor.getConfigurator().apply(config);

        motor.clearStickyFaults();

        motor.optimizeBusUtilization();

        BaseStatusSignal.setUpdateFrequencyForAll(
                50.0,
                velocityRotPerSec,
                tempCelsius,
                appliedVolts,
                supplyCurrentAmps,
                statorCurrentAmps,
                currentPositionRot);
    }

    @Override
    public void updateInputs(IntakePivotIOInputs inputs) {
        BaseStatusSignal.refreshAll(
                velocityRotPerSec,
                tempCelsius,
                appliedVolts,
                supplyCurrentAmps,
                statorCurrentAmps,
                currentPositionRot);

        inputs.velocityRotPerSec = velocityRotPerSec.getValueAsDouble();
        inputs.tempCelsius = tempCelsius.getValue().in(Units.Celsius);
        inputs.appliedVolts = appliedVolts.getValueAsDouble();
        inputs.supplyCurrentAmps = supplyCurrentAmps.getValueAsDouble();
        inputs.statorCurrentAmps = statorCurrentAmps.getValueAsDouble();
        inputs.currentAngleDeg = talonPosToAngle(currentPositionRot.getValueAsDouble());
        inputs.targetAngleDeg = targetAngleDeg;

        if (RobotConstants.TUNING) {
            inputs.intakePivotKP = IntakePivotGainsClass.INTAKE_PIVOT_KP.get();
            inputs.intakePivotKI = IntakePivotGainsClass.INTAKE_PIVOT_KI.get();
            inputs.intakePivotKD = IntakePivotGainsClass.INTAKE_PIVOT_KD.get();

            motor.getConfigurator().apply(new Slot0Configs()
                    .withKP(inputs.intakePivotKP)
                    .withKI(inputs.intakePivotKI)
                    .withKD(inputs.intakePivotKD));
            motionMagicConfigs.MotionMagicCruiseVelocity = INTAKE_PIVOT_CRUISE_VELOCITY.get();
            motionMagicConfigs.MotionMagicAcceleration = INTAKE_PIVOT_ACCELERATION.get();
            motionMagicConfigs.MotionMagicJerk = INTAKE_PIVOT_JERK.get();
            motor.getConfigurator().apply(motionMagicConfigs);
        }
    }

    @Override
    public void setMotorVoltage(double voltage) {
        motor.setControl(voltageOut.withOutput(voltage));
    }

    @Override
    public void setPivotAngle(double targetAngleDeg) {
        this.targetAngleDeg = targetAngleDeg;
        motor.setControl(motionMagic.withPosition(angleToTalonPos(targetAngleDeg)));
    }

    @Override
    public void resetAngle(double resetAngleDeg) {
        motor.setPosition(angleToTalonPos(resetAngleDeg));
    }

    private double angleToTalonPos(double angleDeg) {
        return (angleDeg / 360) * PIVOT_RATIO;
    }

    private double talonPosToAngle(double rotations) {
        return rotations * 360 / PIVOT_RATIO;
    }
}