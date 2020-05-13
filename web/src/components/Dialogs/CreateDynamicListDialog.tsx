import React, { useEffect, useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  makeStyles,
  TextField,
  Typography,
} from '@material-ui/core';
import {
  Event,
  Label,
  OfflineBolt,
  Movie,
  Person,
  SentimentSatisfiedAlt,
  Sort,
  Tv,
} from '@material-ui/icons';
import { FilterParams } from '../../utils/searchFilters';
import {
  Genre,
  ItemType,
  ListDefaultSort,
  ListNetworkRule,
  ListReleaseYearRule,
  ListRule,
  ListRuleType,
  Network,
  NetworkType,
  OpenRange,
} from '../../types';
import _ from 'lodash';
import { prettyItemType, prettySort } from '../Filters/ActiveFilters';
import { useDispatch, useSelector } from 'react-redux';
import { AppState } from '../../reducers';
import { createList } from '../../actions/lists';
import CreateAListValidator from '../../utils/validation/CreateAListValidator';
import { collect } from '../../utils/collection-utils';
import { useGenres, useNetworks } from '../../hooks/useStateMetadata';

const useStyles = makeStyles(theme => ({
  button: {
    margin: theme.spacing(1),
    whiteSpace: 'nowrap',
  },
  filterContainer: {
    marginTop: theme.spacing(1),
  },
  icon: {
    paddingRight: theme.spacing(0.5),
  },
  title: {
    backgroundColor: theme.palette.primary.main,
    padding: theme.spacing(1, 2),
  },
}));

interface OwnProps {
  open: boolean;
  onClose: () => void;
  filters: FilterParams;
  prefilledName?: string;
}

type Props = OwnProps;

export default function CreateDynamicListDialog(props: Props) {
  const classes = useStyles();
  let personNameBySlugOrId = useSelector(
    (state: AppState) => state.people.nameByIdOrSlug,
  );
  let [exited, setExited] = useState(false);
  let [listName, setListName] = useState(props.prefilledName || '');
  let [nameDuplicateError, setNameDuplicateError] = useState(false);
  let [nameLengthError, setNameLengthError] = useState(false);
  let existingLists = useSelector((state: AppState) => state.lists.listsById);
  let networks = useNetworks();
  let genres = useGenres();

  let dispatch = useDispatch();

  const { filters } = props;

  useEffect(() => {
    if (props.open) {
      setExited(false);
    }
  }, [props.open]);

  const handleModalClose = () => {
    setExited(true);
    props.onClose();
  };

  const createListRulesFromFilters = () => {
    let rules: ListRule[] = [];
    if (filters.itemTypes) {
      rules = rules.concat(
        filters.itemTypes.map(type => ({
          itemType: type,
          type: ListRuleType.UserListItemTypeRule,
        })),
      );
    }

    if (filters.networks) {
      rules = rules.concat(
        filters.networks
          .map(network => {
            let foundNetwork = _.find(networks || [], n => n.slug === network);
            if (foundNetwork) {
              return {
                networkId: foundNetwork.id,
                type: ListRuleType.UserListNetworkRule,
              };
            } else {
              return null;
            }
          })
          .filter(x => !_.isUndefined(x)) as ListNetworkRule[],
      );
    }

    if (filters.genresFilter) {
      rules = rules.concat(
        filters.genresFilter.map(genre => {
          return {
            genreId: genre,
            type: ListRuleType.UserListGenreRule,
          };
        }),
      );
    }

    if (filters.sliders && filters.sliders.releaseYear) {
      rules = rules.concat({
        minimum: filters.sliders.releaseYear.min,
        maximum: filters.sliders.releaseYear.max,
        type: ListRuleType.UserListReleaseYearRule,
      } as ListReleaseYearRule);
    }

    if (filters.people && filters.people.length > 0) {
      rules = rules.concat(
        filters.people.map(person => {
          return {
            personSlug: person,
            type: ListRuleType.UserListPersonRule,
          };
        }),
      );
    }

    return rules;
  };

  const renderLabels = <T extends any>(
    icon: any,
    key: string,
    labelType: string,
    labels: T[],
    extractLabel: (label: T) => string,
    pluralize: (labelType: string) => string = t => t + 's',
  ) => {
    if (labels.length === 0) {
      return null;
    } else {
      if (labels.length > 1) {
        labelType = pluralize(labelType);
      }

      let u = (
        <div>
          <span style={{ paddingRight: 8 }}>{labelType}:</span>
          <span>
            {labels.map((label, idx, all) => {
              return (
                <React.Fragment key={extractLabel(label)}>
                  <span style={{ paddingRight: 4 }}>{extractLabel(label)}</span>
                  {idx + 1 < all.length ? (
                    <b style={{ paddingRight: 4 }}> OR </b>
                  ) : null}
                </React.Fragment>
              );
            })}
          </span>
        </div>
      );

      return (
        <ListItem key={key}>
          <ListItemIcon>{icon}</ListItemIcon>
          <ListItemText primary={u} />
        </ListItem>
      );
    }
  };

  const renderGenreRules = (genreIds: number[]) => {
    let actualGenres = collect(genreIds, genreId =>
      _.find(genres, g => g.id === genreId),
    );

    return renderLabels(
      <Label />,
      'genre_rules',
      'Genre',
      actualGenres,
      genre => genre.name,
    );
  };

  const renderNetworkRules = (networkTypes: NetworkType[]) => {
    let actualNetworks = collect(networkTypes, networkId =>
      _.find(networks, g => g.slug === networkId),
    );

    return renderLabels(
      <Tv />,
      'network_rules',
      'Network',
      actualNetworks,
      network => network.name,
    );
  };

  const renderImdbRules = (ratingState: OpenRange) => {
    if (!ratingState.min && !ratingState.max) {
      return null;
    } else if (ratingState.min && !ratingState.max) {
      return (
        <ListItem key={'rating_rule'}>
          <ListItemIcon>
            <SentimentSatisfiedAlt />
          </ListItemIcon>
          <ListItemText
            primary={`IMDB Rating Greater Than: ${ratingState.min}`}
          />
        </ListItem>
      );
    } else if (!ratingState.min && ratingState.max) {
      return (
        <ListItem key={'rating_rule'}>
          <ListItemIcon>
            <SentimentSatisfiedAlt />
          </ListItemIcon>
          <ListItemText primary={`IMDB Rating Less Than: ${ratingState.max}`} />
        </ListItem>
      );
    } else {
      return (
        <ListItem key={'rating_rule'}>
          <ListItemIcon>
            <SentimentSatisfiedAlt />
          </ListItemIcon>
          <ListItemText
            primary={`IMDB Rating Between: ${ratingState.min} and ${ratingState.max}`}
          />
        </ListItem>
      );
    }
  };

  const renderPersonRules = (people: string[]) => {
    let actualPeople = collect(people, person => personNameBySlugOrId[person]);

    return renderLabels(
      <Person />,
      'person_rules',
      'Starring',
      actualPeople,
      _.identity,
      t => t,
    );
  };

  const renderItemTypes = (types: ItemType[]) => {
    return renderLabels(
      <Movie />,
      'type_rules',
      'Type',
      types,
      prettyItemType,
      t => t,
    );
  };

  const renderSortRule = (sort: ListDefaultSort) => {
    return (
      <ListItem key={'sort_rule'}>
        <ListItemIcon>
          <Sort />
        </ListItemIcon>
        <ListItemText primary={`Sort by: ${prettySort(sort.sort)}`} />
      </ListItem>
    );
  };

  const renderReleaseDates = (releaseState: OpenRange) => {
    if (!releaseState.min && !releaseState.max) {
      return null;
    } else if (releaseState.min && !releaseState.max) {
      return (
        <ListItem key={'release_date_rule'}>
          <ListItemIcon>
            <Event />
          </ListItemIcon>
          <ListItemText primary={`Released After: ${releaseState.min}`} />
        </ListItem>
      );
    } else if (!releaseState.min && releaseState.max) {
      return (
        <ListItem key={'release_date_rule'}>
          <ListItemIcon>
            <Event />
          </ListItemIcon>
          <ListItemText primary={`Released Before: ${releaseState.max}`} />
        </ListItem>
      );
    } else {
      return (
        <ListItem key={'release_date_rule'}>
          <ListItemIcon>
            <Event />
          </ListItemIcon>
          <ListItemText
            primary={`Released Between: ${releaseState.min} and ${releaseState.max}`}
          />
        </ListItem>
      );
    }
  };

  const defaultSort: ListDefaultSort | undefined = filters.sortOrder
    ? { sort: filters.sortOrder }
    : undefined;

  const validateListName = () => {
    let validateResult = CreateAListValidator.validate(existingLists, listName);

    setNameDuplicateError(validateResult.nameDuplicateError);
    setNameLengthError(validateResult.nameLengthError);

    return validateResult.hasError();
  };

  const submitListCreate = () => {
    setNameDuplicateError(false);
    setNameLengthError(false);

    if (!validateListName()) {
      dispatch(
        createList({
          name: listName,
          rules: {
            rules: createListRulesFromFilters(),
            sort: defaultSort,
          },
        }),
      );

      handleModalClose();
    }
  };

  return (
    <Dialog
      aria-labelledby="create-dynamic-list-dialog"
      aria-describedby="create-dynamic-list-dialog"
      open={props.open && !exited}
      onClose={handleModalClose}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle id="create-dynamic-list-dialog" className={classes.title}>
        <OfflineBolt className={classes.icon} />
        Create Smart List
      </DialogTitle>
      <DialogContent>
        <FormControl style={{ width: '100%' }}>
          <TextField
            autoFocus
            margin="dense"
            id="name"
            label="Name"
            type="text"
            fullWidth
            value={listName}
            error={nameDuplicateError || nameLengthError}
            onChange={e => setListName(e.target.value)}
          />
          <FormHelperText
            id="component-error-text"
            style={{
              display: nameDuplicateError || nameLengthError ? 'block' : 'none',
            }}
          >
            {nameLengthError ? 'List name cannot be blank' : null}
            {nameDuplicateError
              ? 'You already have a list with this name'
              : null}
          </FormHelperText>
        </FormControl>

        <div className={classes.filterContainer}>
          <Typography>Smart List filters:</Typography>
          <List>
            {filters.genresFilter
              ? renderGenreRules(filters.genresFilter)
              : null}
            {filters.networks ? renderNetworkRules(filters.networks) : null}
            {filters.itemTypes ? renderItemTypes(filters.itemTypes) : null}
            {filters.people ? renderPersonRules(filters.people) : null}
            {filters.sliders && filters.sliders.releaseYear
              ? renderReleaseDates(filters.sliders.releaseYear)
              : null}
            {filters.sliders && filters.sliders.imdbRating
              ? renderImdbRules(filters.sliders.imdbRating)
              : null}
            {defaultSort ? renderSortRule(defaultSort) : null}
          </List>
        </div>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleModalClose} className={classes.button}>
          Cancel
        </Button>
        <Button
          onClick={submitListCreate}
          color="primary"
          variant="contained"
          className={classes.button}
        >
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}
