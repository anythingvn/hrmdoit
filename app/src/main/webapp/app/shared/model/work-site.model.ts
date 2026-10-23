import { WorkLocationType } from 'app/shared/model/enumerations/work-location-type.model';

export interface IWorkSite {
  id?: number;
  name?: string;
  type?: keyof typeof WorkLocationType;
  address?: string | null;
  active?: boolean;
  latitude?: number | null;
  longitude?: number | null;
  radiusM?: number;
  officeIps?: string | null;
}

export const defaultValue: Readonly<IWorkSite> = {
  active: false,
};
