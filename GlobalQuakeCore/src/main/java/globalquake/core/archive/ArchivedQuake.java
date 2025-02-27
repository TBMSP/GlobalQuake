package globalquake.core.archive;

import globalquake.core.GlobalQuake;
import globalquake.core.Settings;
import globalquake.core.earthquake.data.Earthquake;
import globalquake.core.earthquake.data.Hypocenter;
import globalquake.core.analysis.Event;
import globalquake.core.earthquake.quality.QualityClass;
import globalquake.core.regions.RegionUpdater;
import globalquake.core.regions.Regional;
import globalquake.utils.GeoUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArchivedQuake implements Serializable, Comparable<ArchivedQuake>, Regional {

	@Serial
	private static final long serialVersionUID = 6690311245585670539L;

	private final double lat;
	private final double lon;
	private final double depth;
	private final long origin;
	private final double mag;
	private final UUID uuid;
	private final QualityClass qualityClass;
	private double maxRatio;
	private double maxPGA;
	private String region;

	private final ArrayList<ArchivedEvent> archivedEvents;

	private boolean wrong;

	private transient RegionUpdater regionUpdater;
	private static final ExecutorService pgaService = Executors.newSingleThreadExecutor();

	@Serial
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();

		regionUpdater = new RegionUpdater(this);
	}

	public ArchivedQuake(Earthquake earthquake) {
		this(earthquake.getUuid(), earthquake.getLat(), earthquake.getLon(), earthquake.getDepth(), earthquake.getMag(),
				earthquake.getOrigin(),
				earthquake.getHypocenter() == null || earthquake.getHypocenter().quality == null ? null :
						earthquake.getHypocenter().quality.getSummary());
		copyEvents(earthquake);
	}

	public void updateRegion(){
		regionUpdater.updateRegion();
	}

	private void copyEvents(Earthquake earthquake) {
		if(earthquake.getCluster() == null){
			return;
		}
		Hypocenter previousHypocenter = earthquake.getCluster().getPreviousHypocenter();
		if (earthquake.getCluster().getAssignedEvents() == null || previousHypocenter == null) {
			return;
		}

		this.maxRatio = 1;
		for (Event e : earthquake.getCluster().getAssignedEvents().values()) {
			if(e.isValid()) {
				archivedEvents.add(
						new ArchivedEvent(e.getLatFromStation(), e.getLonFromStation(), e.maxRatio, e.getpWave()));
				if (e.maxRatio > this.maxRatio) {
					this.maxRatio = e.getMaxRatio();
				}
			}
		}
	}

	public ArchivedQuake(UUID uuid, double lat, double lon, double depth, double mag, long origin, QualityClass qualityClass) {
		this.uuid = uuid;
		this.lat = lat;
		this.lon = lon;
		this.depth = depth;
		this.mag = mag;
		this.origin = origin;
		this.archivedEvents = new ArrayList<>();
		this.qualityClass = qualityClass;
		regionUpdater = new RegionUpdater(this);
		this.maxPGA = 0.0;

		pgaService.submit(this::calculatePGA);


	}

	private void calculatePGA() {
		double distGEO = globalquake.core.regions.Regions.getOceanDistance(lat, lon, false, depth);
		this.maxPGA = GeoUtils.pgaFunction(mag, distGEO, depth);
	}

	public double getDepth() {
		return depth;
	}

	public double getLat() {
		return lat;
	}

	public double getLon() {
		return lon;
	}

	public double getMag() {
		return mag;
	}

	public long getOrigin() {
		return origin;
	}

	@SuppressWarnings("unused")
    public int getAssignedStations() {
		return archivedEvents == null ? 0 : archivedEvents.size();
	}

	@SuppressWarnings("unused")
	public ArrayList<ArchivedEvent> getArchivedEvents() {
		return archivedEvents;
	}

	@SuppressWarnings("unused")
	public double getMaxRatio() {
		return maxRatio;
	}

	@Override
	public String getRegion() {
		return region;
	}

	public UUID getUuid() {
		return uuid;
	}

	public QualityClass getQualityClass() {
		return qualityClass;
	}

	@Override
	public void setRegion(String newRegion) {
		this.region = newRegion;
	}

	public boolean isWrong() {
		return wrong;
	}

	public void setWrong(boolean wrong) {
		this.wrong = wrong;
	}

	@Override
	public int compareTo(ArchivedQuake archivedQuake) {
		return Long.compare(archivedQuake.getOrigin(), this.getOrigin());
	}

	public double getMaxPGA() {
		return maxPGA;
	}

	public boolean shouldBeDisplayed() {
		if(qualityClass.ordinal() > Settings.qualityFilter){
			return false;
		}

		if (Settings.oldEventsMagnitudeFilterEnabled && getMag() < Settings.oldEventsMagnitudeFilter) {
			return false;
		}

		return !Settings.oldEventsTimeFilterEnabled || !((GlobalQuake.instance.currentTimeMillis() - getOrigin()) > 1000 * 60 * 60L * Settings.oldEventsTimeFilter);
	}

	@Override
	public String toString() {
		return "ArchivedQuake{" +
				"lat=" + lat +
				", lon=" + lon +
				", depth=" + depth +
				", origin=" + origin +
				", mag=" + mag +
				", uuid=" + uuid +
				", qualityClass=" + qualityClass +
				", maxRatio=" + maxRatio +
				", region='" + region + '\'' +
				", wrong=" + wrong +
				'}';
	}
}
