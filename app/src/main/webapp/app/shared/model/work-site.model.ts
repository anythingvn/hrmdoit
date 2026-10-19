import { WorkLocationType } from 'app/shared/model/enumerations/work-location-type.model';

export interface IWorkSite {
  id?: number;
  name?: string;
  type?: keyof typeof WorkLocationType;
  address?: string | null;
  active?: boolean;
}

export const defaultValue: Readonly<IWorkSite> = {
  active: false,
};
